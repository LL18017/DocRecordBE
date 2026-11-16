package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-31 · Agenda del medico: mis citas, rango semanal, inasistencia y la cita
 * que queda ATENDIDA al iniciar la consulta desde ella.
 *
 * Una cita no se puede agendar en el pasado, asi que las que necesitan que su
 * hora ya haya pasado se mueven directo en la base despues de agendarlas.
 */
class AgendaDelMedicoIT extends PruebaClinica {

    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");

    @Autowired private JdbcTemplate jdbc;

    private String medico;
    private int medicoId;
    private int sede;
    private long paciente;
    private int tipo;

    @BeforeEach
    void prepararLaAgenda() throws Exception {
        medico = tokenDeMedico();
        sede = crearSede(medico);
        paciente = crearPaciente(medico);

        String cuerpo = mockMvc.perform(post("/tipos-cita")
                        .header("Authorization", bearer(tokenDeAdministrador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Control %d"}
                                """.formatted(siguiente())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        tipo = json.readTree(cuerpo).get("tipoCitaId").asInt();
        medicoId = medicoDeLaSede(medico, sede);
    }

    private int medicoDeLaSede(String token, int clinicaId) throws Exception {
        String cuerpo = mockMvc.perform(get("/citas/medicos?clinicaId=" + clinicaId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get(0).get("medicoId").asInt();
    }

    private static LocalDate pasadoManana() {
        return LocalDate.now(ZONA).plusDays(2);
    }

    private JsonNode agendar(String token, long pacienteId, int medico, int clinica, int hora) throws Exception {
        String cuerpo = mockMvc.perform(post("/citas")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"fechaHora":"%s","tipoCitaId":%d,"medicoId":%d,"clinicaId":%d,
                                 "motivo":"Control de presion arterial"}
                                """.formatted(pacienteId, pasadoManana().atTime(hora, 0), tipo, medico, clinica)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private int agendarMia(int hora) throws Exception {
        return agendar(medico, paciente, medicoId, sede, hora).get("citaId").asInt();
    }

    /** Mueve la cita al pasado (anteayer a la hora dada) directo en la base. */
    private void moverAlPasado(int citaId, int hora) {
        jdbc.update("UPDATE citas SET fecha_hora = ? WHERE cita_id = ?",
                LocalDate.now(ZONA).minusDays(2).atTime(hora, 0), citaId);
    }

    private JsonNode leer(String token, String ruta) throws Exception {
        String cuerpo = mockMvc.perform(get(ruta).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private ResultActions marcarNoAsistio(String token, int citaId) throws Exception {
        return mockMvc.perform(patch("/citas/" + citaId + "/no-asistio").header("Authorization", bearer(token)));
    }

    private ResultActions iniciarConsulta(String token, long pacienteId, int citaId) throws Exception {
        return mockMvc.perform(post("/consultas")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pacienteId":%d,"citaId":%d,"motivo":"Control de presion","diagnostico":"Hipertension controlada"}
                        """.formatted(pacienteId, citaId)));
    }

    // ── Mis citas y rango ──────────────────────────────────────────────────

    @Test
    @DisplayName("HU-31: mias=true devuelve solo las citas del médico autenticado, con sede y motivo")
    void misCitasSoloLasDelMedico() throws Exception {
        agendarMia(9);

        String otroMedico = tokenDeMedico();
        int otraSede = crearSede(otroMedico);
        int otroMedicoId = medicoDeLaSede(otroMedico, otraSede);
        agendar(otroMedico, crearPaciente(otroMedico), otroMedicoId, otraSede, 10);

        // medicoId de otro se ignora: "mis citas" sale del token.
        JsonNode mias = leer(medico, "/citas?mias=true&medicoId=" + otroMedicoId + "&desde=" + pasadoManana());
        assertEquals(1, mias.size());
        assertEquals(medicoId, mias.get(0).get("medicoId").asInt());
        assertEquals(sede, mias.get(0).get("clinicaId").asInt());
        assertTrue(mias.get(0).get("clinica").asText().length() > 0);
        assertEquals("Control de presion arterial", mias.get(0).get("motivo").asText());

        JsonNode lasDelOtro = leer(otroMedico, "/citas?mias=true&desde=" + pasadoManana());
        assertEquals(1, lasDelOtro.size());
        assertEquals(otroMedicoId, lasDelOtro.get(0).get("medicoId").asInt());
    }

    @Test
    @DisplayName("HU-31: mias=true sin ser médico es 400")
    void misCitasSoloParaMedicos() throws Exception {
        mockMvc.perform(get("/citas?mias=true").header("Authorization", bearer(tokenDeEnfermera())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/citas?mias=true").header("Authorization", bearer(tokenDeAdminQueNoEjerce())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("HU-31: la vista semanal trae las citas entre desde y hasta; sin fechas es el día de hoy")
    void rangoSemanal() throws Exception {
        agendarMia(9);
        LocalDate hoy = LocalDate.now(ZONA);

        assertEquals(1, leer(medico, "/citas?mias=true&desde=" + hoy + "&hasta=" + hoy.plusDays(6)).size());
        assertEquals(0, leer(medico, "/citas?mias=true&desde=" + hoy + "&hasta=" + hoy.plusDays(1)).size());
        assertEquals(0, leer(medico, "/citas?mias=true").size(), "sin fechas es hoy y la cita es pasado mañana");
        assertEquals(1, leer(medico, "/citas?mias=true&desde=" + hoy + "&hasta=" + hoy.plusDays(30)).size(),
                "31 días contando ambos extremos todavía se permiten");
    }

    @Test
    @DisplayName("HU-31: un rango de más de 31 días es 400")
    void rangoDeMasDe31DiasEs400() throws Exception {
        LocalDate hoy = LocalDate.now(ZONA);
        String cuerpo = mockMvc.perform(get("/citas?mias=true&desde=" + hoy + "&hasta=" + hoy.plusDays(31))
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertTrue(cuerpo.contains("El rango de la agenda no puede superar 31 días"), cuerpo);

        mockMvc.perform(get("/citas?clinicaId=" + sede + "&desde=" + hoy + "&hasta=" + hoy.plusDays(40))
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isBadRequest());
    }

    // ── NO_ASISTIO ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-31: el médico marca NO_ASISTIO una cita agendada cuya hora ya pasó, y luego no se reprograma ni cancela")
    void noAsistioCuandoLaHoraPaso() throws Exception {
        int citaId = agendarMia(9);
        moverAlPasado(citaId, 9);

        String cuerpo = marcarNoAsistio(medico, citaId)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals("NO_ASISTIO", json.readTree(cuerpo).get("estado").asText());
        assertEquals("NO_ASISTIO", leer(medico, "/citas/" + citaId).get("estado").asText());

        mockMvc.perform(patch("/citas/" + citaId + "/cancelacion")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"motivo":"Se intenta cancelar una inasistencia"}
                                """))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/citas/" + citaId)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fechaHora":"%s"}
                                """.formatted(pasadoManana().atTime(11, 0))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("HU-31: NO_ASISTIO antes de la hora de la cita es 409")
    void noAsistioAntesDeLaHoraEs409() throws Exception {
        int citaId = agendarMia(10);

        String cuerpo = marcarNoAsistio(medico, citaId)
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(cuerpo.contains("Todavía no llega la hora de la cita"), cuerpo);
        assertEquals("AGENDADA", leer(medico, "/citas/" + citaId).get("estado").asText());
    }

    @Test
    @DisplayName("HU-31: NO_ASISTIO de una cita que no está AGENDADA es 409")
    void noAsistioSoloDesdeAgendada() throws Exception {
        int citaId = agendarMia(11);
        mockMvc.perform(patch("/citas/" + citaId + "/cancelacion")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"motivo":"El paciente avisa que viaja"}
                                """))
                .andExpect(status().isOk());
        moverAlPasado(citaId, 11);

        String cuerpo = marcarNoAsistio(medico, citaId)
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(cuerpo.contains("esta cita está CANCELADA"), cuerpo);
    }

    @Test
    @DisplayName("HU-31: NO_ASISTIO lo niega con 403 a otro médico y a enfermería de otra sede; el administrador sí puede")
    void noAsistioPermisos() throws Exception {
        int citaId = agendarMia(12);
        moverAlPasado(citaId, 12);

        marcarNoAsistio(tokenDeMedico(), citaId).andExpect(status().isForbidden());
        marcarNoAsistio(tokenDeEnfermera(), citaId).andExpect(status().isForbidden());
        assertEquals("AGENDADA", leer(medico, "/citas/" + citaId).get("estado").asText());

        marcarNoAsistio(tokenDeAdministrador(), citaId).andExpect(status().isOk());
        marcarNoAsistio(medico, 999999).andExpect(status().isNotFound());
    }

    // ── ATENDIDA al iniciar la consulta ────────────────────────────────────

    @Test
    @DisplayName("HU-31: la consulta iniciada con citaId deja la cita ATENDIDA y guarda cita_id")
    void consultaDesdeLaCitaLaMarcaAtendida() throws Exception {
        int citaId = agendarMia(14);
        tomarSignosVitales(paciente);

        String cuerpo = iniciarConsulta(medico, paciente, citaId)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode consulta = json.readTree(cuerpo);
        assertEquals(citaId, consulta.get("citaId").asInt());
        assertEquals(sede, consulta.get("clinica").get("clinicaId").asInt(), "sin clinicaId toma la sede de la cita");

        assertEquals("ATENDIDA", leer(medico, "/citas/" + citaId).get("estado").asText());
        Integer guardada = jdbc.queryForObject(
                "SELECT cita_id FROM consultas WHERE consulta_id = ?", Integer.class,
                consulta.get("consultaId").asLong());
        assertEquals(citaId, guardada);

        // Una cita atendida no inicia otra consulta, ni se reprograma ni se cancela.
        iniciarConsulta(medico, paciente, citaId).andExpect(status().isConflict());
        mockMvc.perform(put("/citas/" + citaId)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fechaHora":"%s"}
                                """.formatted(pasadoManana().atTime(16, 0))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("HU-31: sin signos de 24 h la consulta con citaId sigue siendo 409 y la cita no cambia")
    void consultaDesdeLaCitaRespetaLaToma() throws Exception {
        int citaId = agendarMia(15);

        iniciarConsulta(medico, paciente, citaId).andExpect(status().isConflict());
        assertEquals("AGENDADA", leer(medico, "/citas/" + citaId).get("estado").asText());
    }

    @Test
    @DisplayName("HU-31: citaId de otro paciente es 409")
    void citaDeOtroPacienteEs409() throws Exception {
        int citaId = agendarMia(15);
        long otroPaciente = crearPaciente(medico);
        tomarSignosVitales(otroPaciente);

        String cuerpo = iniciarConsulta(medico, otroPaciente, citaId)
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(cuerpo.contains("La cita no corresponde a este paciente"), cuerpo);
        assertEquals("AGENDADA", leer(medico, "/citas/" + citaId).get("estado").asText());
    }

    @Test
    @DisplayName("HU-31: iniciar la consulta desde la cita de otro médico es 403")
    void citaAjenaEs403() throws Exception {
        String otroMedico = tokenDeMedico();
        int otraSede = crearSede(otroMedico);
        int otroMedicoId = medicoDeLaSede(otroMedico, otraSede);
        int citaAjena = agendar(otroMedico, paciente, otroMedicoId, otraSede, 16).get("citaId").asInt();
        tomarSignosVitales(paciente);

        iniciarConsulta(medico, paciente, citaAjena).andExpect(status().isForbidden());
        assertEquals("AGENDADA", leer(medico, "/citas/" + citaAjena).get("estado").asText());
    }
}
