package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.repository.UserRepository;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        User usuario = usuarios.findByEmailContainingIgnoreCase(correo)
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
        User medico = usuarios.findByEmailContainingIgnoreCase(correoDelMedico)
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
        mockMvc.perform(get("/especialidades")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/clinics/mias")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/user/all")).andExpect(status().isUnauthorized());
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
}
