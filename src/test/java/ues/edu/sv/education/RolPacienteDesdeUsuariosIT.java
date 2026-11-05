package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.enums.RolesEnum;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El rol PACIENTE desde Usuarios y Roles (HU-34, portal del paciente).
 *
 * Una cuenta creada en Usuarios y Roles nace con una persona que solo tiene
 * nombre y apellido: sin DUI, asi que Pacientes no la encuentra al buscar por
 * DUI. Darle el rol PACIENTE abre su expediente sobre ESA persona, con la
 * fecha de nacimiento y el sexo que se piden en el mismo paso, y la cuenta
 * entra a su portal. Sin DUI y sin crear una persona nueva.
 */
class RolPacienteDesdeUsuariosIT extends PruebaClinica {

    private static final int PACIENTE = RolesEnum.PACIENTE.getId();

    @Autowired private JdbcTemplate jdbc;

    private String admin;

    @BeforeEach
    void preparar() throws Exception {
        admin = tokenDeAdministrador();
    }

    @Test
    @DisplayName("una cuenta sin DUI recibe el rol PACIENTE: se abre su expediente con su misma persona y entra a su portal")
    void laCuentaSinDuiQuedaComoPaciente() throws Exception {
        String correo = correoUnico("usuarios.paciente");
        int userId = altaPorUsuariosYRoles(correo);
        long persona = personaDe(userId);

        JsonNode respuesta = cuerpo(darRolPaciente(userId, "fechaNacimiento", "1988-03-14", "sexo", "M")
                .andExpect(status().isOk()));
        assertTrue(respuesta.hasNonNull("expediente"), "la respuesta trae el expediente abierto");
        assertTrue(roles(userId).contains("PACIENTE"));

        // El expediente es de la MISMA persona: no se creo otra.
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT expediente, fecha_nacimiento::text AS fecha, sexo, email, dui "
                        + "FROM pacientes JOIN persona USING (persona_id) WHERE persona_id = ?", persona);
        assertEquals(respuesta.get("expediente").asText(), fila.get("expediente"));
        assertEquals("1988-03-14", fila.get("fecha"));
        assertEquals("M", fila.get("sexo"));
        assertEquals(null, fila.get("dui"), "no se pide DUI");
        assertEquals(correo, fila.get("email"), "el correo de la cuenta queda como contacto");

        // Un paciente no trabaja en ninguna sede.
        assertEquals(0, sedesDe(userId));

        // Con la sesion nueva entra a su portal y ve su propio expediente.
        JsonNode exp = cuerpo(mockMvc.perform(get("/mi/expediente").header("Authorization", bearer(iniciarSesion(correo))))
                .andExpect(status().isOk()));
        assertEquals(persona, exp.get("paciente").get("personaId").asLong());
    }

    @Test
    @DisplayName("sin fecha de nacimiento o sin sexo no se asigna nada: 422 y la cuenta queda como estaba")
    void sinFechaOSexoNoSeAsignaNada() throws Exception {
        int userId = altaPorUsuariosYRoles(correoUnico("usuarios.incompleto"));
        long persona = personaDe(userId);

        darRolPaciente(userId, "sexo", "F").andExpect(status().isUnprocessableEntity());
        darRolPaciente(userId, "fechaNacimiento", "1990-01-01").andExpect(status().isUnprocessableEntity());

        assertFalse(roles(userId).contains("PACIENTE"), "no quedo el rol a medias");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM pacientes WHERE persona_id = ?", Integer.class, persona));
    }

    @Test
    @DisplayName("un sexo que no es M ni F responde 400 y no abre nada")
    void sexoInvalido() throws Exception {
        int userId = altaPorUsuariosYRoles(correoUnico("usuarios.sexo"));

        darRolPaciente(userId, "fechaNacimiento", "1990-01-01", "sexo", "X").andExpect(status().isBadRequest());

        assertFalse(roles(userId).contains("PACIENTE"));
    }

    @Test
    @DisplayName("si la persona ya tenia expediente solo se agrega el rol: no se duplica ni se piden fecha y sexo")
    void conExpedienteSoloSeAgregaElRol() throws Exception {
        String correo = correoUnico("usuarios.yapaciente");
        User medico = crearMedicoHabilitado(correo, CLAVE, "Medico", "Ya Paciente");
        long persona = medico.getPersona().getPersonaId();
        String expediente = json.readTree(mockMvc.perform(post("/pacientes")
                        .header("Authorization", bearer(tokenDeMedico()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"persona":{"personaId":%d,"dui":"%s","fechaNacimiento":"1980-06-01","sexo":"M"}}
                                """.formatted(persona, duiUnico())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("expediente").asText();

        JsonNode respuesta = cuerpo(darRolPaciente(medico.getUserID()).andExpect(status().isOk()));

        assertEquals(expediente, respuesta.get("expediente").asText());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pacientes WHERE persona_id = ?", Integer.class, persona));
        assertTrue(roles(medico.getUserID()).containsAll(List.of("MEDICO", "PACIENTE")), "conserva su rol de medico");
    }

    @Test
    @DisplayName("con clinicaId el expediente se abre en esa sede, pero la cuenta no recibe la sede")
    void laSedeEsDelExpedienteNoDeLaCuenta() throws Exception {
        int sede = crearSede(admin);
        int userId = altaPorUsuariosYRoles(correoUnico("usuarios.sede"));

        darRolPaciente(userId, "clinicaId", String.valueOf(sede), "fechaNacimiento", "1975-11-30", "sexo", "F")
                .andExpect(status().isOk());

        assertEquals(sede, jdbc.queryForObject(
                "SELECT registrado_en_clinica_id FROM pacientes WHERE persona_id = ?", Integer.class, personaDe(userId)));
        assertEquals(0, sedesDe(userId));
    }

    @Test
    @DisplayName("el listado de usuarios dice quien ya tiene expediente")
    void elListadoTraeElExpediente() throws Exception {
        String correo = correoUnico("usuarios.listado");
        int userId = altaPorUsuariosYRoles(correo);
        String expediente = cuerpo(darRolPaciente(userId, "fechaNacimiento", "2000-02-02", "sexo", "F")
                .andExpect(status().isOk())).get("expediente").asText();

        JsonNode pagina = cuerpo(mockMvc.perform(get("/user/all").param("inicio", "0").param("fin", "1000")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk()));
        JsonNode fila = null;
        for (JsonNode u : pagina) {
            if (u.get("userId").asInt() == userId) fila = u;
        }
        assertTrue(fila != null, "la cuenta sale en el listado");
        assertEquals(expediente, fila.get("expediente").asText());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    /** El alta real de Usuarios y Roles (solo nombre, correo y clave) y la cuenta habilitada. */
    private int altaPorUsuariosYRoles(String correo) throws Exception {
        int userId = cuerpo(mockMvc.perform(post("/user")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","userName":"Cuenta Sin Dui","password":"%s"}
                                """.formatted(correo, CLAVE)))
                .andExpect(status().is2xxSuccessful())).get("userId").asInt();
        mockMvc.perform(post("/user/{id}/password", userId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"%s\"}".formatted(CLAVE)))
                .andExpect(status().isOk());
        return userId;
    }

    private ResultActions darRolPaciente(int userId, String... parametros) throws Exception {
        var peticion = post("/user/{u}/role/{r}", userId, PACIENTE).header("Authorization", bearer(admin));
        for (int i = 0; i < parametros.length; i += 2) {
            peticion = peticion.param(parametros[i], parametros[i + 1]);
        }
        return mockMvc.perform(peticion);
    }

    private long personaDe(int userId) {
        return jdbc.queryForObject("SELECT persona_id FROM users WHERE user_id = ?", Long.class, userId);
    }

    private List<String> roles(int userId) {
        return jdbc.queryForList(
                "SELECT r.name FROM user_roles ur JOIN role r ON r.role_id = ur.role_id WHERE ur.user_id = ?",
                String.class, userId);
    }

    private int sedesDe(int userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM clinica_personal WHERE user_id = ?", Integer.class, userId);
    }

    private JsonNode cuerpo(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }
}
