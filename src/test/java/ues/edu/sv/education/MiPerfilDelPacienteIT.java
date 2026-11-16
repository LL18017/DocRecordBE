package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DEF-07 (DRS-101) · "Mi perfil" del paciente: HU-33 c4 (preferencia de
 * recordatorios) y HU-14 c4 (sus habitos en solo lectura).
 *
 * El paciente entra por el camino real, como en PortalDelPacienteIT: se
 * registra, el personal le da acceso al portal, canjea la invitacion e inicia
 * sesion. Que el recordatorio no salga con la preferencia apagada lo prueba
 * AvisosDeCitaIT.
 */
class MiPerfilDelPacienteIT extends PruebaClinica {

    private static final String PERFIL = "/api/mi-perfil";

    @Autowired private JdbcTemplate jdbc;

    private String medico;
    private String enfermera;
    private String admin;

    @BeforeEach
    void prepararElPersonal() throws Exception {
        medico = tokenDeMedico();
        enfermera = tokenDeEnfermera();
        admin = tokenDeAdministrador();
    }

    @Test
    @DisplayName("HU-33: el paciente ve su perfil con recibeRecordatorios=true por defecto")
    void elPacienteVeSuPerfil() throws Exception {
        String correo = correoUnico("perfil.paciente");
        long pacienteId = pacienteConCorreo(correo);
        String paciente = tokenDelPaciente(correo, pacienteId);

        JsonNode perfil = cuerpo(mockMvc.perform(get(PERFIL).header("Authorization", bearer(paciente)))
                .andExpect(status().isOk()));

        assertEquals(pacienteId, perfil.get("pacienteId").asLong());
        assertTrue(perfil.hasNonNull("expediente"));
        assertEquals("Paciente", perfil.get("nombres").asText());
        assertEquals("Con Perfil", perfil.get("apellidos").asText());
        assertEquals(correo, perfil.get("correo").asText());
        assertEquals("7777-8888", perfil.get("telefono").asText());
        assertTrue(perfil.get("recibeRecordatorios").asBoolean());
    }

    @Test
    @DisplayName("HU-33 c4: el paciente apaga y vuelve a encender sus recordatorios con PUT (y PATCH)")
    void elPacienteCambiaSuPreferencia() throws Exception {
        String correo = correoUnico("perfil.preferencia");
        long pacienteId = pacienteConCorreo(correo);
        String paciente = tokenDelPaciente(correo, pacienteId);

        mockMvc.perform(put(PERFIL)
                        .header("Authorization", bearer(paciente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recibeRecordatorios\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recibeRecordatorios").value(false));

        mockMvc.perform(get(PERFIL).header("Authorization", bearer(paciente)))
                .andExpect(jsonPath("$.recibeRecordatorios").value(false));
        assertEquals(Boolean.FALSE, jdbc.queryForObject(
                "SELECT recibe_recordatorios FROM pacientes WHERE persona_id = ?", Boolean.class, pacienteId));

        // Solo cambia la preferencia: otros campos del cuerpo se ignoran.
        mockMvc.perform(patch(PERFIL)
                        .header("Authorization", bearer(paciente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recibeRecordatorios\":true,\"nombres\":\"Otro Nombre\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recibeRecordatorios").value(true))
                .andExpect(jsonPath("$.nombres").value("Paciente"));
    }

    @Test
    @DisplayName("HU-33 c4: sin recibeRecordatorios en el cuerpo responde 400")
    void sinPreferenciaEs400() throws Exception {
        String correo = correoUnico("perfil.vacio");
        String paciente = tokenDelPaciente(correo, pacienteConCorreo(correo));

        mockMvc.perform(put(PERFIL)
                        .header("Authorization", bearer(paciente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Indique si desea recibir recordatorios (true o false)"));
    }

    @Test
    @DisplayName("HU-33: /api/mi-perfil es solo del rol PACIENTE: el medico, la enfermera y el admin reciben 403")
    void otrosRolesReciben403() throws Exception {
        for (String token : List.of(medico, enfermera, admin)) {
            mockMvc.perform(get(PERFIL).header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put(PERFIL)
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recibeRecordatorios\":false}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(PERFIL + "/habitos").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(PERFIL)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("HU-14 c4: /api/mi-perfil/habitos trae solo los habitos activos propios, sin los anulados ni los de otro")
    void misHabitosSoloLosPropios() throws Exception {
        String correo = correoUnico("perfil.habitos");
        long pacienteId = pacienteConCorreo(correo);
        long otroPaciente = crearPaciente(medico);
        int tipo = crearTipoHabito();

        long activo = crearHabito(pacienteId, tipo, "Camina 30 minutos");
        long anulado = crearHabito(pacienteId, tipo, "Fuma a diario");
        long ajeno = crearHabito(otroPaciente, tipo, "Hábito de otro paciente");
        mockMvc.perform(patch("/api/habitos-estilo-de-vida/{id}/anulacion", anulado)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("motivo", "Registrado en el paciente equivocado"))))
                .andExpect(status().isOk());

        String paciente = tokenDelPaciente(correo, pacienteId);
        JsonNode habitos = cuerpo(mockMvc.perform(get(PERFIL + "/habitos").header("Authorization", bearer(paciente)))
                .andExpect(status().isOk()));

        assertTrue(contiene(habitos, activo));
        assertFalse(contiene(habitos, anulado), "los anulados no se muestran en el portal");
        assertFalse(contiene(habitos, ajeno), "nunca los de otro paciente");
        for (JsonNode habito : habitos) {
            assertEquals(pacienteId, habito.get("pacienteId").asLong());
            assertEquals("ACTIVO", habito.get("estado").asText());
        }
    }

    @Test
    @DisplayName("HU-14 c4: el paciente no gana acceso a los endpoints de habitos ni puede escribir en ellos")
    void elPacienteNoEntraALosEndpointsDeHabitos() throws Exception {
        String correo = correoUnico("perfil.sinacceso");
        long pacienteId = pacienteConCorreo(correo);
        long habito = crearHabito(pacienteId, crearTipoHabito(), "Hábito de solo lectura");
        String paciente = tokenDelPaciente(correo, pacienteId);

        mockMvc.perform(get("/api/habitos-estilo-de-vida/paciente/{id}", pacienteId)
                        .header("Authorization", bearer(paciente)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/habitos-estilo-de-vida/{id}", habito)
                        .header("Authorization", bearer(paciente)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/habitos-estilo-de-vida/{id}/anulacion", habito)
                        .header("Authorization", bearer(paciente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("motivo", "El paciente quiere borrarlo"))))
                .andExpect(status().isForbidden());
        // /habitos del perfil es solo lectura.
        mockMvc.perform(post(PERFIL + "/habitos")
                        .header("Authorization", bearer(paciente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(PERFIL).header("Authorization", bearer(paciente)))
                .andExpect(status().isMethodNotAllowed());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    private long pacienteConCorreo(String correo) throws Exception {
        JsonNode creado = cuerpo(mockMvc.perform(post("/pacientes")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"persona":{"dui":"%s","nombres":"Paciente","apellidos":"Con Perfil",
                                            "fechaNacimiento":"1990-05-20","sexo":"F","email":"%s",
                                            "telefono":"7777-8888"},
                                 "tipoSangre":"O+"}
                                """.formatted(duiUnico(), correo)))
                .andExpect(status().isCreated()));
        return creado.get("personaId").asLong();
    }

    /** El camino real: acceso desde el expediente, invitacion canjeada, login. */
    private String tokenDelPaciente(String correo, long pacienteId) throws Exception {
        mockMvc.perform(post("/pacientes/{id}/acceso-portal", pacienteId)
                        .header("Authorization", bearer(enfermera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(correo)))
                .andExpect(status().isOk());
        List<String> tokens = jdbc.queryForList("""
                select i.token from invitaciones i join users u on u.user_id = i.user_id
                where lower(u.email) = lower(?) and i.usada = false
                """, String.class, correo);
        assertEquals(1, tokens.size(), "debe haber exactamente una invitacion vigente");
        mockMvc.perform(post("/auth/invitacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(tokens.get(0), CLAVE)))
                .andExpect(status().isNoContent());
        return iniciarSesion(correo);
    }

    private int crearTipoHabito() throws Exception {
        return cuerpo(mockMvc.perform(post("/api/habitos-estilo-de-vida/tipos")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Tipo de perfil %d\"}".formatted(siguiente())))
                .andExpect(status().isCreated())).get("id").asInt();
    }

    private long crearHabito(long pacienteId, int tipo, String descripcion) throws Exception {
        return cuerpo(mockMvc.perform(post("/api/habitos-estilo-de-vida")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descripcion":"%s","nivel":"BAJO","frecuencia":"Diaria",
                                 "pacienteId":%d,"tipoHabitoId":%d}
                                """.formatted(descripcion, pacienteId, tipo)))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode cuerpo(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private static boolean contiene(JsonNode lista, long id) {
        for (JsonNode elemento : lista) {
            if (elemento.get("id").asLong() == id) return true;
        }
        return false;
    }
}
