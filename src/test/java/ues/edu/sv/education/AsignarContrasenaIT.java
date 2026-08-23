package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.enums.RolesEnum;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /user/{userId}/password: un administrador asigna una contrasena nueva
 * a otro usuario, o a si mismo.
 *
 * Nace junto con la confirmacion por correo de POST /user (ver el comentario
 * de UserService.createUser). Cada prueba corresponde a una de las tres
 * decisiones de diseno de asignarContrasena, mas las mismas garantias de
 * seguridad que ya cubre AltaDeUsuarioIT para el alta:
 *
 *   1. asignar contrasena TAMBIEN habilita la cuenta -salida para cuando el
 *      correo de confirmacion nunca llego-.
 *   2. un administrador NO puede asignarle contrasena a OTRO administrador
 *      -evita que una cuenta ADMIN comprometida controle a las demas-.
 *   3. un administrador SI puede asignarse contrasena a si mismo.
 *   4. solo ADMIN llega al endpoint -MEDICO y ENFERMERA reciben 403-.
 *   5. la contrasena nueva funciona en el login y la vieja deja de servir.
 *   6. ninguna respuesta de este endpoint expone la contrasena.
 *
 * No usa PruebaClinica.tokenDeAdminQueNoEjerce() para los casos entre dos
 * administradores porque ese metodo no devuelve el correo de la cuenta que
 * crea, y esta clase necesita el correo Y el token de cada administrador para
 * poder iniciar sesion con la clave nueva. crearAdmin() replica el mismo alta
 * -Persona + User con rol ADMIN, habilitado, login real por HTTP- devolviendo
 * los dos datos juntos.
 */
class AsignarContrasenaIT extends PruebaClinica {

    @Autowired private JdbcTemplate jdbc;

    private record Admin(String correo, String token) {}

    private Admin admin;

    @BeforeEach
    void autenticarUnAdministrador() throws Exception {
        admin = crearAdmin("clave.admin");
    }

    private Admin crearAdmin(String etiqueta) throws Exception {
        String correo = correoUnico(etiqueta);
        Persona persona = personas.saveAndFlush(Persona.builder()
                .nombres("Admin").apellidos("De Prueba")
                .build());
        usuarios.saveAndFlush(User.builder()
                .persona(persona)
                .email(correo)
                .password(encoder.encode(CLAVE))
                .enabled(true)
                .roles(new HashSet<>(Set.of(roles.getReferenceById(RolesEnum.ADMIN.getId()))))
                .build());

        String cuerpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(correo, CLAVE)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();

        return new Admin(correo, json.readTree(cuerpo).get("token").asText());
    }

    private JsonNode crearCuenta(String token, String correo, String clave) throws Exception {
        String cuerpo = mockMvc.perform(post("/user")
                        .header("Authorization", bearer(token))
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
                .orElseThrow(() -> new AssertionError("no se encontro el usuario " + correo));
    }

    private String loginBody(String correo, String clave) throws Exception {
        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(correo, clave)))
                .andReturn().getResponse().getContentAsString();
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. Asignar contrasena tambien habilita la cuenta
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("asignar una contrasena habilita una cuenta que aun no habia confirmado su correo")
    void asignarContrasenaHabilitaLaCuenta() throws Exception {
        String correo = correoUnico("clave.habilita");
        JsonNode alta = crearCuenta(admin.token(), correo, CLAVE);
        assertFalse(leerDeLaBase(correo).isEnabled(),
                "la cuenta recien creada por POST /user debe nacer deshabilitada");

        String nuevaClave = "OtraClave2026!";
        mockMvc.perform(post("/user/{id}/password", alta.get("userId").asInt())
                        .header("Authorization", bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"%s"}
                                """.formatted(nuevaClave)))
                .andExpect(status().isOk());

        assertTrue(leerDeLaBase(correo).isEnabled(),
                "POST /user/{id}/password debe habilitar la cuenta objetivo");

        JsonNode respuestaLogin = json.readTree(loginBody(correo, nuevaClave));
        assertFalse(respuestaLogin.get("token").asText().isBlank(),
                "la cuenta habilitada por asignacion de contrasena debe poder iniciar sesion");
    }

    @Test
    @DisplayName("asignar una contrasena borra el token de verificacion pendiente")
    void asignarContrasenaBorraElTokenPendiente() throws Exception {
        String correo = correoUnico("clave.borratoken");
        JsonNode alta = crearCuenta(admin.token(), correo, CLAVE);

        mockMvc.perform(post("/user/{id}/password", alta.get("userId").asInt())
                        .header("Authorization", bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"OtraClave2026!"}
                                """))
                .andExpect(status().isOk());

        Integer tokensRestantes = jdbc.queryForObject(
                "select count(*) from verification_token where user_id = ?",
                Integer.class, alta.get("userId").asInt());
        assertEquals(0, tokensRestantes,
                "no debe quedar ningun token de verificacion tras asignar la contrasena a mano");
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2 y 3. Entre administradores: nunca a otro, si a uno mismo
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("un administrador no puede asignarle una contrasena nueva a OTRO administrador")
    void unAdminNoPuedeAsignarleContrasenaAOtroAdmin() throws Exception {
        Admin otroAdmin = crearAdmin("clave.otroadmin");

        mockMvc.perform(post("/user/{id}/password", leerDeLaBase(otroAdmin.correo()).getUserID())
                        .header("Authorization", bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"IntentoAjeno2026!"}
                                """))
                .andExpect(status().isForbidden());

        // Y lo que importa de verdad: la clave de otroAdmin sigue siendo la
        // original, no la que este intento trato de imponer.
        JsonNode respuestaLogin = json.readTree(loginBody(otroAdmin.correo(), CLAVE));
        assertFalse(respuestaLogin.get("token").asText().isBlank(),
                "la contrasena del otro administrador no debio cambiar");
    }

    @Test
    @DisplayName("un administrador SI puede asignarse una contrasena nueva a si mismo")
    void unAdminPuedeAsignarseContrasenaASiMismo() throws Exception {
        int miPropioId = leerDeLaBase(admin.correo()).getUserID();
        String nuevaClave = "MiPropiaClave2026!";

        mockMvc.perform(post("/user/{id}/password", miPropioId)
                        .header("Authorization", bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"%s"}
                                """.formatted(nuevaClave)))
                .andExpect(status().isOk());

        JsonNode respuestaLogin = json.readTree(loginBody(admin.correo(), nuevaClave));
        assertFalse(respuestaLogin.get("token").asText().isBlank(),
                "el administrador debe poder iniciar sesion con la contrasena que se asigno a si mismo");
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. Solo ADMIN llega al endpoint
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("un token de MEDICO recibe 403 al intentar asignar una contrasena")
    void unMedicoRecibe403() throws Exception {
        String correo = correoUnico("clave.objetivo.medico");
        JsonNode alta = crearCuenta(admin.token(), correo, CLAVE);
        String tokenMedico = tokenDeMedico();

        mockMvc.perform(post("/user/{id}/password", alta.get("userId").asInt())
                        .header("Authorization", bearer(tokenMedico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"NoDeberia2026!"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un token de ENFERMERA recibe 403 al intentar asignar una contrasena")
    void unaEnfermeraRecibe403() throws Exception {
        String correo = correoUnico("clave.objetivo.enfermera");
        JsonNode alta = crearCuenta(admin.token(), correo, CLAVE);
        String tokenEnfermera = tokenDeEnfermera();

        mockMvc.perform(post("/user/{id}/password", alta.get("userId").asInt())
                        .header("Authorization", bearer(tokenEnfermera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"NoDeberia2026!"}
                                """))
                .andExpect(status().isForbidden());
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. La contrasena nueva funciona; la vieja deja de servir
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("tras asignar una contrasena nueva, la vieja ya no sirve para iniciar sesion")
    void laContrasenaViejaDejaDeFuncionar() throws Exception {
        String correo = correoUnico("clave.reemplazo");
        JsonNode alta = crearCuenta(admin.token(), correo, CLAVE);
        String nuevaClave = "ReemplazoClave2026!";

        mockMvc.perform(post("/user/{id}/password", alta.get("userId").asInt())
                        .header("Authorization", bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"%s"}
                                """.formatted(nuevaClave)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(correo, CLAVE)))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(correo, nuevaClave)))
                .andExpect(status().is2xxSuccessful());
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6. La respuesta de este endpoint no expone la contrasena
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("la respuesta de POST /user/{id}/password no expone la contrasena")
    void laRespuestaNoExponeLaContrasena() throws Exception {
        String correo = correoUnico("clave.respuesta");
        JsonNode alta = crearCuenta(admin.token(), correo, CLAVE);
        String nuevaClave = "SinExponer2026!";

        String cuerpo = mockMvc.perform(post("/user/{id}/password", alta.get("userId").asInt())
                        .header("Authorization", bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"%s"}
                                """.formatted(nuevaClave)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode respuesta = json.readTree(cuerpo);
        assertTrue(camposLlamadosPassword(respuesta).isEmpty(),
                "la respuesta trae campos de contrasena: " + camposLlamadosPassword(respuesta));
        assertFalse(respuesta.toString().contains(nuevaClave),
                "la respuesta devuelve la contrasena nueva en claro");
        assertFalse(respuesta.toString().contains("$argon2"),
                "la respuesta devuelve el hash de la contrasena");
    }

    // ══════════════════════════════════════════════════════════════════════
    // El correo de confirmacion tampoco bloquea el alta desde este flujo
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("si el correo de confirmacion falla, POST /user crea la cuenta igual y lo reporta")
    void siElCorreoFallaLaCuentaSeCreaIgual() throws Exception {
        doThrow(new MailSendException("SMTP caido a proposito para esta prueba"))
                .when(correoDePruebas).send(any(MimeMessage.class));

        String correo = correoUnico("clave.sincorreo");
        JsonNode alta = crearCuenta(admin.token(), correo, CLAVE);

        assertFalse(alta.get("correoDeVerificacionEnviado").asBoolean(),
                "la respuesta no debe afirmar que el correo salio cuando el envio fallo");
        assertFalse(leerDeLaBase(correo).isEnabled(),
                "la cuenta debe seguir naciendo deshabilitada aunque el correo haya fallado");
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo: recorrido de campos llamados "password" (igual que AltaDeUsuarioIT)
    // ══════════════════════════════════════════════════════════════════════

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
