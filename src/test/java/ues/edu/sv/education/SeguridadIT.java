package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ues.edu.sv.education.model.dto.auth.CustomUserDetails;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.repository.PersonaRepository;
import ues.edu.sv.education.repository.UserRepository;
import ues.edu.sv.education.service.auth.JwtService;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de autorizacion.
 *
 * Cada prueba de esta clase corresponde a un agujero de seguridad REAL que
 * existio en este backend y que se cerro durante la refactorizacion. No son
 * hipotesis: los cuatro se comprobaron explotables antes de arreglarlos. Su
 * funcion aqui es impedir que vuelvan.
 *
 * Se usan tokens JWT de verdad, obtenidos registrando e iniciando sesion por la
 * API, en vez de @WithMockUser. Motivo: el JwtFilter decide por su cuenta que
 * rutas son publicas, asi que una autenticacion simulada que no pase por el
 * filtro probaria una cadena distinta de la que corre en produccion.
 */
@AutoConfigureMockMvc
class SeguridadIT extends PruebaDeIntegracion {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository usuarios;
    @Autowired private PersonaRepository personas;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtService jwtService;
    // Se instancia en vez de inyectarse: este contexto no expone un bean de ObjectMapper.
    private final ObjectMapper json = new ObjectMapper();

    // Cada prueba registra su propio medico. El registro COMMITEA (la clase no
    // es @Transactional a proposito: MockMvc debe ver el usuario ya guardado
    // para poder autenticarlo), asi que reutilizar un correo fijo haria que la
    // segunda prueba chocara con 409 "correo ya registrado".
    private static final AtomicInteger CONTADOR = new AtomicInteger();

    private static final String CLAVE = "Docrecord2026!";

    private String correoDelMedico;
    private String tokenDeMedico;
    private Integer idDelMedico;

    @BeforeEach
    void registrarUnMedicoYAutenticarlo() throws Exception {
        String correo = "medico.pruebas." + CONTADOR.incrementAndGet() + "@ues.edu.sv";
        correoDelMedico = correo;
        String clave = CLAVE;

        // Se pide ADEMAS el rol ADMIN en el cuerpo. Si el servidor lo respetara,
        // esta cuenta seria administradora y el resto de las pruebas pasarian
        // por la razon equivocada.
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Medico","apellidos":"De Pruebas",
                                 "email":"%s","password":"%s",
                                 "especialidadId":1,
                                 "roles":[1],"userType":1}
                                """.formatted(correo, clave)))
                .andExpect(status().is2xxSuccessful());

        // La cuenta nace deshabilitada a la espera del correo de confirmacion.
        User usuario = usuarios.findByEmailIgnoreCase(correo)
                .orElseThrow(() -> new AssertionError("el registro no creo el usuario"));
        usuario.setEnabled(true);
        usuarios.saveAndFlush(usuario);
        idDelMedico = usuario.getUserID();

        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(correo, clave)))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        JsonNode cuerpo = json.readTree(login.getResponse().getContentAsString());
        tokenDeMedico = cuerpo.get("token").asText();
        assertNotNull(tokenDeMedico);
    }

    private String bearer() {
        return "Bearer " + tokenDeMedico;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Agujero 1: el registro publico permitia elegirse el rol
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("el registro publico ignora los roles que pida el cliente")
    void elRegistroPublicoIgnoraLosRolesDelCliente() throws Exception {
        // /auth/** es permitAll. Antes, AuthService tomaba request.roles() y se
        // los asignaba tal cual, asi que cualquiera en internet podia crearse
        // una cuenta ADMIN mandando "roles":[1]. Que la cuenta naciera
        // deshabilitada no protegia nada: el atacante controla su propio correo.
        User medico = usuarios.findByEmailIgnoreCase(correoDelMedico)
                .orElseThrow(() -> new AssertionError("no se encontro el usuario"));

        var roles = medico.getRoles().stream().map(r -> r.getName()).toList();

        assertEquals(1, roles.size(), "el registro publico debe asignar exactamente un rol");
        assertEquals("MEDICO", roles.get(0),
                "pidio ADMIN en el cuerpo y debe haber quedado MEDICO; el servidor decide el rol");
    }

    // ══════════════════════════════════════════════════════════════════════
    // Agujero 2: cualquiera podia asignarse cualquier rol
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("un medico no puede asignarse a si mismo el rol de administrador")
    void unMedicoNoPuedeAsignarseElRolDeAdministrador() throws Exception {
        // Era el peor de los cuatro: POST /user/{id}/role/{id} estaba solo
        // autenticado. Bastaba con tener cuenta -- y cualquiera puede crearsela
        // -- para volverse ADMIN en un solo paso.
        mockMvc.perform(post("/user/{userId}/role/{roleId}", idDelMedico, 1)
                        .header("Authorization", bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un medico no puede listar todos los usuarios del sistema")
    void unMedicoNoPuedeListarTodosLosUsuarios() throws Exception {
        // No es escalada de privilegios pero si fuga de datos personales: expone
        // los correos de todo el personal a cualquier autenticado.
        mockMvc.perform(get("/user/all").param("inicio", "0").param("fin", "50")
                        .header("Authorization", bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un medico no puede crear usuarios")
    void unMedicoNoPuedeCrearUsuarios() throws Exception {
        mockMvc.perform(post("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@ues.edu.sv\",\"userName\":\"X\",\"password\":\"Docrecord2026!\"}")
                        .header("Authorization", bearer()))
                .andExpect(status().isForbidden());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sin token no se entra a ningun lado
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("sin token, los endpoints del dominio clinico responden 401")
    void sinTokenLosEndpointsClinicosRechazan() throws Exception {
        mockMvc.perform(get("/pacientes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/personas").param("dui", "01234567-8"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/clinics/mias")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/user/all")).andExpect(status().isUnauthorized());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Agujero 5: el login decia que correos existen
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("el login responde IGUAL exista o no el correo (HU-01, criterio 2)")
    void elLoginNoDelataQueCorreosExisten() throws Exception {
        // Antes: correo inexistente -> 404 "No se encontro al usuario";
        //        correo real con clave mala -> 401 "Credenciales incorrectas".
        // Con esa diferencia se averigua quien tiene cuenta probando correos,
        // sin adivinar ni una sola contrasena. En un expediente clinico eso
        // dice quien trabaja aqui y, con los correos institucionales, quien es
        // paciente.
        var inexistente = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"no.existe.%d@ues.edu.sv","password":"LoQueSea1!"}
                                """.formatted(CONTADOR.incrementAndGet())))
                .andReturn().getResponse();

        var claveMala = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"EstaNoEsLaClave9!"}
                                """.formatted(correoDelMedico)))
                .andReturn().getResponse();

        assertEquals(claveMala.getStatus(), inexistente.getStatus(),
                "el codigo de estado delata si el correo existe");
        assertEquals(401, inexistente.getStatus(),
                "un intento fallido de sesion es 401, no 404");

        // Se compara el cuerpo ENTERO y no un campo: la diferencia que
        // delataba el correo era de FORMA ademas de contenido -404 con un
        // cuerpo, 401 con otro-, y comparar una sola clave la dejaria pasar.
        // Sigue comparandose entero ahora que TT-01 unifico el formato, porque
        // es lo que detecta una divergencia futura: dos manejadores distintos
        // respondiendo cada uno lo suyo vuelve a delatar cual correo existe.
        assertEquals(claveMala.getContentAsString(), inexistente.getContentAsString(),
                "el cuerpo de la respuesta delata si el correo existe");
    }

    @Test
    @DisplayName("el 401 del filtro responde JSON con el formato uniforme de la API (TT-01)")
    void elRechazoSinTokenRespondeElFormatoUniforme() throws Exception {
        // El filtro corre ANTES del DispatcherServlet, asi que ningun
        // @ExceptionHandler lo alcanza: si el filtro no arma el cuerpo, nadie lo
        // hace. Antes escribia "Debe enviar token Bearer" en texto plano, que
        // dejaba fuera del contrato al caso de error mas comun de la API.
        mockMvc.perform(get("/pacientes"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("No autenticado"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("un token corrupto tambien responde JSON, no texto plano")
    void elTokenInvalidoRespondeElFormatoUniforme() throws Exception {
        mockMvc.perform(get("/pacientes").header("Authorization", "Bearer no-es-un-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("No autenticado"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("el mensaje del 401 esta redactado para el usuario, no para el programador")
    void elMensajeDel401NoHablaDeCabeceras() throws Exception {
        // La interfaz lo muestra tal cual (`extraerMensajeDeError` en
        // lib/api.ts se queda con `message`). Nombrar la cabecera Authorization
        // o el esquema Bearer no le dice nada a quien solo ve que perdio la
        // sesion.
        String cuerpo = mockMvc.perform(get("/pacientes"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String mensaje = json.readTree(cuerpo).get("message").asText().toLowerCase();
        assertFalse(mensaje.contains("bearer"), "el mensaje no debe nombrar el esquema del token");
        assertFalse(mensaje.contains("token"), "ni la palabra token");
        assertFalse(mensaje.contains("header"), "ni la cabecera HTTP");
        assertTrue(mensaje.contains("sesion") || mensaje.contains("sesión"),
                "debe hablar de la sesion, que es lo que la persona reconoce");
    }

    @Test
    @DisplayName("el catalogo de especialidades SI es publico, porque lo usa el registro")
    void elCatalogoDeEspecialidadesEsPublico() throws Exception {
        // Excepcion deliberada a "todo requiere token": /register es una
        // pantalla publica y necesita este catalogo para llenar su selector.
        // Exigirle sesion dejaba el <select> en "Cargando especialidades..."
        // para siempre y hacia imposible crear una cuenta desde la interfaz.
        //
        // Es seguro: son nombres de especialidades medicas, sin dato personal.
        // Esta prueba existe para que nadie lo "corrija" cerrandolo de nuevo
        // sin darse cuenta de que rompe el registro.
        mockMvc.perform(get("/especialidades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(7))))
                .andExpect(jsonPath("$[0].nombre").exists());
    }

    @Test
    @DisplayName("solo la lectura del catalogo es publica, no su modificacion")
    void soloLaLecturaDelCatalogoEsPublica() throws Exception {
        // Abrir GET no debe abrir el resto del recurso.
        mockMvc.perform(post("/especialidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Especialidad Intrusa\",\"activa\":true}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("un token inventado no sirve para entrar")
    void unTokenInventadoNoSirve() throws Exception {
        mockMvc.perform(get("/pacientes")
                        .header("Authorization", "Bearer esto.no.es.un.token"))
                .andExpect(status().isUnauthorized());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Lo que el medico SI debe poder hacer
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("un medico si puede consultar el dominio clinico")
    void unMedicoSiPuedeConsultarElDominioClinico() throws Exception {
        // Contrapeso necesario: sin esta prueba, cerrar todo con 403 tambien
        // pasaria las anteriores. Aqui se comprueba que las reglas distinguen,
        // no que bloqueen todo.
        mockMvc.perform(get("/especialidades").header("Authorization", bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/pacientes").header("Authorization", bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/clinics/mias").header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("una ruta inexistente responde 404, no 500")
    void unaRutaInexistenteResponde404() throws Exception {
        // El GlobalExceptionHandler convertia cualquier excepcion en 500. Un 500
        // le dice al cliente "el servidor esta roto, reintenta"; un 404 le dice
        // "eso no existe".
        mockMvc.perform(get("/esto-no-existe").header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Autorizacion en los bordes del token
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("un token ya expirado no sirve para entrar")
    void unTokenExpiradoNoSirve() throws Exception {
        // Se fabrica con JwtService directamente, con una expiracion negativa:
        // es un token con firma valida (la misma clave de siempre) pero
        // vencido desde el instante en que se genero. JwtService.getClaims
        // lanza ExpiredJwtException al leerlo, que JwtFilter atrapa junto con
        // cualquier otro fallo y responde 401.
        //
        // User.persona es LAZY (ver User.java) y esta clase no es
        // @Transactional a proposito (el registro de @BeforeEach necesita
        // comitear): fuera de una sesion de Hibernate abierta, tocar
        // usuario.getPersona().getNombres() revienta con
        // LazyInitializationException. Se resuelve pidiendo la Persona aparte
        // por su id -- leer el id de un proxy lazy NO lo inicializa -- y
        // reemplazandola en el User antes de construir el token.
        User usuario = usuarios.findByEmailIgnoreCase(correoDelMedico)
                .orElseThrow(() -> new AssertionError("no se encontro el usuario"));
        usuario.setPersona(personas.findById(usuario.getPersona().getPersonaId())
                .orElseThrow(() -> new AssertionError("no se encontro la persona")));
        CustomUserDetails detalles = new CustomUserDetails(usuario);
        String tokenExpirado = jwtService.buildToken(detalles, -1000L);

        mockMvc.perform(get("/pacientes").header("Authorization", "Bearer " + tokenExpirado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un token con la firma alterada no sirve para entrar")
    void unTokenConLaFirmaAlteradaNoSirve() throws Exception {
        // Se toma un token real y valido y se cambia UN caracter dentro de su
        // firma (el tercer segmento del JWT). Jwts.parser().verifyWith(...)
        // debe rechazarlo por SignatureException, no aceptarlo ni caer en un
        // 500: es la diferencia entre "invalido porque nunca existio" (el
        // "token inventado" que ya prueba unTokenInventadoNoSirve) y
        // "invalido porque alguien lo manipulo".
        String[] segmentos = tokenDeMedico.split("\\.");
        assertEquals(3, segmentos.length, "un JWT debe tener tres segmentos separados por punto");

        // Se altera el PRIMER caracter de la firma, no el ultimo, y la razon es
        // aritmetica: la firma de HS256 son 32 bytes, que en base64url ocupan 43
        // caracteres. 43 x 6 = 258 bits para 256 de datos, asi que al ULTIMO
        // caracter solo le corresponden 2 bits significativos y los otros 4 son
        // relleno. Cambiar 'A' (000000) por 'B' (000001) ahi no cambia ningun
        // byte: los dos decodifican exactamente igual, el token sigue siendo
        // valido y la prueba pasaba sin haber alterado nada.
        //
        // Esta prueba venia siendo no determinista por eso: el token cambia en
        // cada ejecucion, y solo fallaba cuando el ultimo caracter caia en uno
        // de esos pares que colisionan. En el primer caracter los seis bits son
        // significativos y el cambio siempre llega al byte.
        char primero = segmentos[2].charAt(0);
        char alterado = primero == 'A' ? 'B' : 'A';
        String firmaAlterada = alterado + segmentos[2].substring(1);
        String tokenAlterado = segmentos[0] + "." + segmentos[1] + "." + firmaAlterada;

        // Y se comprueba que de verdad quedo alterado, en vez de darlo por
        // hecho: si alguna vez vuelve a ser un cambio nulo, el fallo dira que
        // la prueba no probo nada -- no que el sistema acepta firmas falsas.
        assertNotEquals(tokenDeMedico, tokenAlterado, "la firma tenia que quedar distinta");

        mockMvc.perform(get("/pacientes").header("Authorization", "Bearer " + tokenAlterado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un token sin ningun rol autentica pero no autoriza: 403, no 401")
    void unTokenSinNingunRolRecibe403NoConfundirConSinToken() throws Exception {
        // Distinto de "sin token" (401, ver sinTokenLosEndpointsClinicosRechazan):
        // aqui SI hay una sesion valida -JwtFilter la acepta y deja el
        // principal en el SecurityContext- pero el arreglo "authorities" del
        // token viene vacio, asi que ningun hasAnyRole(...) se cumple. Eso es
        // 403 (quien eres se sabe, no te alcanza), no 401 (no se sabe quien
        // eres).
        String correo = "medico.sinrol." + CONTADOR.incrementAndGet() + "@ues.edu.sv";
        Persona persona = personas.saveAndFlush(
                Persona.builder().nombres("Sin").apellidos("Ningun Rol").build());
        usuarios.saveAndFlush(User.builder()
                .persona(persona)
                .email(correo)
                .password(encoder.encode(CLAVE))
                .enabled(true)
                .roles(new HashSet<>())
                .build());

        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(correo, CLAVE)))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        String tokenSinRoles = json.readTree(login.getResponse().getContentAsString())
                .get("token").asText();

        mockMvc.perform(get("/pacientes").header("Authorization", "Bearer " + tokenSinRoles))
                .andExpect(status().isForbidden());
    }
}
