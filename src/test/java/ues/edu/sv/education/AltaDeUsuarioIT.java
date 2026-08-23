package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import ues.edu.sv.education.model.entity.User;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Alta de cuentas por un administrador: POST /user y el listado /user.
 *
 * Cada prueba de esta clase corresponde a una fuga REAL que existio en este
 * backend, comprobada leyendo el codigo y la base de desarrollo antes de
 * arreglarla, igual que las de SeguridadIT. Su funcion es impedir que vuelvan:
 *
 *   1. POST /user guardaba la contrasena SIN CIFRAR. En la base aparecia
 *      "Docrecord2026!" legible en users.password.
 *   2. GET /user devolvia la entidad JPA cruda, con el hash argon2 de cada
 *      usuario dentro del JSON.
 *
 * Se prueba por HTTP con MockMvc y token real, no llamando al servicio: lo que
 * importa es lo que sale por el cable, que es exactamente donde estaba la fuga
 * numero 2 -- el servicio devolvia lo correcto y era Jackson, al serializar la
 * entidad, quien filtraba el hash.
 *
 * Extiende PruebaClinica por una sola razon: necesita un token ADMIN. Todo el
 * controller /user es hasRole('ADMIN') y /auth/register solo crea medicos, asi
 * que un administrador hay que fabricarlo por repositorio y despues iniciar
 * sesion por HTTP; eso es justo lo que hace tokenDeAdminQueNoEjerce().
 */
class AltaDeUsuarioIT extends PruebaClinica {

    @Autowired private JdbcTemplate jdbc;

    private String tokenDeAdmin;

    @BeforeEach
    void autenticarUnAdministrador() throws Exception {
        tokenDeAdmin = tokenDeAdminQueNoEjerce();
    }

    /** Da de alta una cuenta por POST /user y devuelve la respuesta ya leida. */
    private JsonNode crearCuenta(String correo, String clave) throws Exception {
        String cuerpo = mockMvc.perform(post("/user")
                        .header("Authorization", bearer(tokenDeAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","userName":"Personal De Turno","password":"%s"}
                                """.formatted(correo, clave)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private User leerDeLaBase(String correo) {
        return usuarios.findByEmailContainingIgnoreCase(correo)
                .orElseThrow(() -> new AssertionError("POST /user no creo el usuario " + correo));
    }

    /** El token de verificacion sin usar que quedo para esta cuenta. Ver CorreoDeConfirmacionIT. */
    private String tokenGuardadoDe(String correo) {
        List<String> tokens = jdbc.queryForList(
                "select t.token from verification_token t join users u on u.user_id = t.user_id "
                        + "where upper(u.email) = upper(?) and t.used = false",
                String.class, correo);
        assertEquals(1, tokens.size(), "la cuenta debe quedar con un solo token sin usar");
        return tokens.get(0);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Fuga 1: la contrasena se guardaba en texto plano
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("la contrasena que guarda POST /user no es la que se envio")
    void laContrasenaNoSeGuardaEnTextoPlano() throws Exception {
        String correo = correoUnico("alta.cifrado");

        crearCuenta(correo, CLAVE);

        String guardada = leerDeLaBase(correo).getPassword();

        // Las tres afirmaciones dicen cosas distintas y hacen falta las tres:
        // la primera descarta el texto plano, la segunda que sea argon2 y no
        // cualquier transformacion reversible (un Base64, un ROT13), y la
        // tercera que el hash corresponda a ESTA contrasena y no a otra.
        assertNotEquals(CLAVE, guardada,
                "users.password quedo con la contrasena legible");
        assertTrue(guardada.startsWith("$argon2"),
                "se esperaba un hash argon2 y se obtuvo: " + guardada);
        assertTrue(encoder.matches(CLAVE, guardada),
                "el hash guardado no corresponde a la contrasena enviada");
    }

    @Test
    @DisplayName("la cuenta creada por POST /user puede iniciar sesion una vez confirmada por correo")
    void laCuentaCreadaPuedeAutenticarseSiSeHabilita() throws Exception {
        // El cifrado no sirve de nada si rompe el inicio de sesion. Se confirma
        // por el mismo enlace que recibiria la persona -GET /auth/confirm- y no
        // fijando enabled a mano: eso probaria una cuenta habilitada por un
        // atajo que /auth/login nunca ve en produccion.
        String correo = correoUnico("alta.login");
        JsonNode alta = crearCuenta(correo, CLAVE);
        assertTrue(alta.get("correoDeVerificacionEnviado").asBoolean(),
                "con el emisor simulado funcionando, el correo debe reportarse como enviado");

        mockMvc.perform(get("/auth/confirm").param("token", tokenGuardadoDe(correo)))
                .andExpect(status().is3xxRedirection());

        assertTrue(leerDeLaBase(correo).isEnabled(),
                "GET /auth/confirm con un token valido debe habilitar la cuenta");

        String respuesta = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(correo, CLAVE)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();

        assertFalse(json.readTree(respuesta).get("token").asText().isBlank(),
                "el inicio de sesion no devolvio token");
    }

    // ══════════════════════════════════════════════════════════════════════
    // Fuga 2: las respuestas de /user llevaban el hash de cada usuario
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("la respuesta de POST /user no expone la contrasena")
    void laRespuestaDelAltaNoExponeLaContrasena() throws Exception {
        String correo = correoUnico("alta.respuesta");

        JsonNode respuesta = crearCuenta(correo, CLAVE);

        assertTrue(camposLlamadosPassword(respuesta).isEmpty(),
                "la respuesta del alta trae campos de contrasena: "
                        + camposLlamadosPassword(respuesta));
        assertFalse(respuesta.toString().contains(CLAVE),
                "la respuesta del alta devuelve la contrasena en claro");
        assertFalse(respuesta.toString().contains("$argon2"),
                "la respuesta del alta devuelve el hash de la contrasena");
    }

    @Test
    @DisplayName("el listado GET /user/all no expone la contrasena de nadie")
    void elListadoNoExponeLaContrasena() throws Exception {
        // El listado devuelve a TODOS los usuarios, asi que una entidad cruda
        // aqui no filtra un hash sino el de todo el personal de golpe.
        crearCuenta(correoUnico("alta.listado"), CLAVE);

        String cuerpo = mockMvc.perform(get("/user/all")
                        .param("inicio", "0").param("fin", "200")
                        .header("Authorization", bearer(tokenDeAdmin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode listado = json.readTree(cuerpo);

        assertTrue(listado.size() > 0, "el listado vino vacio, no prueba nada");
        assertTrue(camposLlamadosPassword(listado).isEmpty(),
                "el listado trae campos de contrasena: " + camposLlamadosPassword(listado));
        assertFalse(cuerpo.contains("$argon2"),
                "el listado devuelve hashes argon2");
        assertFalse(cuerpo.contains(CLAVE),
                "el listado devuelve contrasenas en claro");
    }

    @Test
    @DisplayName("GET /user ya no existe: el listado de entidades crudas se elimino")
    void elListadoDeEntidadesCrudasYaNoSeExpone() throws Exception {
        // Era el mismo listado que /user/all pero devolviendo List<User>, la
        // entidad JPA: incluia el hash argon2 de todos los usuarios y ademas
        // reventaba al serializar el ciclo user -> roles -> users. Se elimino
        // en vez de convertirlo a DTO porque /user/all ya cubria el caso y
        // nadie lo llamaba.
        //
        // Responde 405 y no 404 porque la ruta /user sigue mapeada para POST.
        // Lo que importa no es el codigo exacto sino que NO sea 200: si
        // alguien reintroduce el @GetMapping, esta prueba se pone en rojo.
        mockMvc.perform(get("/user")
                        .header("Authorization", bearer(tokenDeAdmin)))
                .andExpect(status().isMethodNotAllowed());
    }

    // ══════════════════════════════════════════════════════════════════════
    // El cuerpo invalido se rechaza nombrando los campos del cliente
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("un alta con correo invalido responde 400 nombrando email, no campos de Persona")
    void unAltaInvalidaSenalaElCampoQueElClienteEnvio() throws Exception {
        // Sin @Valid en el controller, este cuerpo pasaba de largo y reventaba
        // mas adentro, al guardar la Persona: el 400 nombraba "nombres" y
        // "apellidos", campos que el cliente nunca envio y que no puede marcar
        // en su formulario.
        String cuerpo = mockMvc.perform(post("/user")
                        .header("Authorization", bearer(tokenDeAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"esto-no-es-un-correo","userName":"Sin Correo","password":"%s"}
                                """.formatted(CLAVE)))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        JsonNode errores = json.readTree(cuerpo);

        assertTrue(errores.has("email"),
                "el 400 debe senalar el campo email; llego: " + cuerpo);
        assertFalse(errores.has("nombres") || errores.has("apellidos"),
                "el 400 nombra campos internos que el cliente nunca envio: " + cuerpo);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Recorre el JSON completo y devuelve la ruta de todo campo que se llame
     * password (o parecido). Se busca por nombre y no comparando con el valor
     * enviado porque el peligro real es el hash de OTROS usuarios, cuya
     * contrasena esta prueba no conoce.
     */
    private List<String> camposLlamadosPassword(JsonNode nodo) {
        List<String> encontrados = new ArrayList<>();
        recolectar(nodo, "$", encontrados);
        return encontrados;
    }

    private void recolectar(JsonNode nodo, String ruta, List<String> encontrados) {
        if (nodo.isObject()) {
            nodo.properties().forEach(campo -> {
                String nombre = campo.getKey().toLowerCase();
                if (nombre.contains("password") || nombre.contains("contrasena")
                        || nombre.equals("clave")) {
                    encontrados.add(ruta + "." + campo.getKey());
                }
                recolectar(campo.getValue(), ruta + "." + campo.getKey(), encontrados);
            });
        } else if (nodo.isArray()) {
            for (int i = 0; i < nodo.size(); i++) {
                recolectar(nodo.get(i), ruta + "[" + i + "]", encontrados);
            }
        }
    }
}
