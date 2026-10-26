package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-30 y HU-32 · Agendar, reprogramar y cancelar citas (DRS-29, DRS-99).
 *
 * El paciente es uno de expediente, creado por crearPaciente(): no tiene
 * cuenta. Antes de V24 una cita solo podia agendarse a un usuario, y esta
 * prueba no habria podido ni empezar.
 */
class AgendaDeCitasIT extends PruebaClinica {

    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");

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
                                {"nombre":"Consulta general %d"}
                                """.formatted(siguiente())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        tipo = json.readTree(cuerpo).get("tipoCitaId").asInt();

        JsonNode medicos = leer("/citas/medicos?clinicaId=" + sede);
        assertEquals(1, medicos.size(), "el medico que registro la sede atiende en ella");
        medicoId = medicos.get(0).get("medicoId").asInt();
    }

    private JsonNode leer(String ruta) throws Exception {
        String cuerpo = mockMvc.perform(get(ruta).header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    /** Pasado mañana a la hora dada: lejos de la regla de las 4 horas. */
    private static String pasadoManana(int hora, int minuto) {
        return LocalDate.now(ZONA).plusDays(2).atTime(hora, minuto).toString();
    }

    private ResultActions intentarAgendar(long pacienteId, String fechaHora) throws Exception {
        return mockMvc.perform(post("/citas")
                .header("Authorization", bearer(medico))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pacienteId":%d,"fechaHora":"%s","tipoCitaId":%d,"medicoId":%d,"clinicaId":%d}
                        """.formatted(pacienteId, fechaHora, tipo, medicoId, sede)));
    }

    private JsonNode agendar(long pacienteId, String fechaHora) throws Exception {
        String cuerpo = intentarAgendar(pacienteId, fechaHora)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private ResultActions intentarCancelar(int citaId, String motivo) throws Exception {
        return mockMvc.perform(patch("/citas/" + citaId + "/cancelacion")
                .header("Authorization", bearer(medico))
                .contentType(MediaType.APPLICATION_JSON)
                .content(motivo == null ? "{}" : """
                        {"motivo":"%s"}
                        """.formatted(motivo)));
    }

    // ── HU-30 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-30 c4: se agenda a un paciente sin cuenta y queda AGENDADA, en su sede")
    void unaCitaNaceAgendada() throws Exception {
        JsonNode cita = agendar(paciente, pasadoManana(9, 0));

        assertEquals("AGENDADA", cita.get("estado").asText());
        assertEquals(paciente, cita.get("pacienteId").asLong());
        assertEquals(medicoId, cita.get("medicoId").asInt());
        assertEquals(sede, cita.get("clinicaId").asInt());
        assertTrue(cita.get("expediente").asText().length() > 0);
    }

    @Test
    @DisplayName("HU-30 c2 y c5: una franja ocupada del medico se rechaza con 409, no con 500")
    void laFranjaOcupadaSeRechaza() throws Exception {
        agendar(paciente, pasadoManana(10, 0));
        long otroPaciente = crearPaciente(medico);

        intentarAgendar(otroPaciente, pasadoManana(10, 0))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("HU-30 c2: el mismo paciente no puede tener dos citas a la misma hora")
    void elPacienteNoSeDuplica() throws Exception {
        agendar(paciente, pasadoManana(11, 0));

        // Otro medico, misma hora: lo que choca es el paciente.
        String otroMedico = tokenDeMedico();
        int otraSede = crearSede(otroMedico);
        String cuerpo = mockMvc.perform(get("/citas/medicos?clinicaId=" + otraSede)
                        .header("Authorization", bearer(otroMedico)))
                .andReturn().getResponse().getContentAsString();
        int otroMedicoId = json.readTree(cuerpo).get(0).get("medicoId").asInt();

        mockMvc.perform(post("/citas")
                        .header("Authorization", bearer(otroMedico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"fechaHora":"%s","tipoCitaId":%d,"medicoId":%d,"clinicaId":%d}
                                """.formatted(paciente, pasadoManana(11, 0), tipo, otroMedicoId, otraSede)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("HU-30 c3: fecha pasada, fuera de la grilla o mal escrita se rechazan con 400")
    void lasFechasSeValidan() throws Exception {
        intentarAgendar(paciente, LocalDate.now(ZONA).minusDays(1).atTime(9, 0).toString())
                .andExpect(status().isBadRequest());
        intentarAgendar(paciente, pasadoManana(9, 15))
                .andExpect(status().isBadRequest());
        intentarAgendar(paciente, "manana a las nueve")
                .andExpect(status().isBadRequest());
    }

    // ── HU-32 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-32 c1 y c5: cancelar exige motivo, no borra la cita y libera la franja")
    void cancelarConservaLaCitaYLiberaLaFranja() throws Exception {
        int citaId = agendar(paciente, pasadoManana(14, 0)).get("citaId").asInt();

        intentarCancelar(citaId, null).andExpect(status().isBadRequest());

        String cuerpo = intentarCancelar(citaId, "El paciente viaja ese dia")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode cancelada = json.readTree(cuerpo);
        assertEquals("CANCELADA", cancelada.get("estado").asText());
        assertEquals("El paciente viaja ese dia", cancelada.get("motivoCancelacion").asText());

        // La franja quedo libre para otro paciente...
        agendar(crearPaciente(medico), pasadoManana(14, 0));

        // ...y la cancelada sigue en la agenda, con su motivo.
        JsonNode agenda = leer("/citas?clinicaId=" + sede + "&desde=" + LocalDate.now(ZONA).plusDays(2));
        assertEquals(2, agenda.size());
        assertTrue(agenda.findValuesAsText("estado").contains("CANCELADA"));

        intentarCancelar(citaId, "otra vez").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("HU-32 c2: con menos de 4 horas no se cancela ni se reprograma")
    void laAnticipacionMinimaSeRespeta() throws Exception {
        // Esta prueba corre a cualquier hora, y desde HU-29 una cita fuera del
        // horario del medico se rechaza: se le da un horario de dia completo
        // todos los dias para que lo unico que decida sea la anticipacion.
        String admin = tokenDeAdministrador();
        for (int dia = 1; dia <= 7; dia++) {
            mockMvc.perform(post("/clinics/" + sede + "/medicos/" + medicoId + "/horarios")
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"diaSemana":%d,"horaInicio":"00:00","horaFin":"23:30"}
                                    """.formatted(dia)))
                    .andExpect(status().isCreated());
        }

        // La siguiente franja de 30 minutos que empiece despues de una hora.
        LocalDateTime pronto = LocalDateTime.now(ZONA).plusHours(1).truncatedTo(ChronoUnit.HOURS).plusHours(1);
        int citaId = agendar(paciente, pronto.toString()).get("citaId").asInt();

        intentarCancelar(citaId, "Imprevisto").andExpect(status().isConflict());

        mockMvc.perform(put("/citas/" + citaId)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fechaHora":"%s"}
                                """.formatted(pasadoManana(8, 0))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("HU-32 c3: reprogramar aplica las mismas validaciones que agendar")
    void reprogramarValidaComoAgendar() throws Exception {
        agendar(crearPaciente(medico), pasadoManana(15, 0));
        int citaId = agendar(paciente, pasadoManana(15, 30)).get("citaId").asInt();

        mockMvc.perform(put("/citas/" + citaId)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fechaHora":"%s"}
                                """.formatted(pasadoManana(15, 0))))
                .andExpect(status().isConflict());

        String cuerpo = mockMvc.perform(put("/citas/" + citaId)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fechaHora":"%s"}
                                """.formatted(pasadoManana(16, 0))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(pasadoManana(16, 0) + ":00", json.readTree(cuerpo).get("fechaHora").asText());
    }

    @Test
    @DisplayName("la agenda pide la clinica o el medico, y una cita inexistente es 404")
    void filtrosYNoEncontrados() throws Exception {
        mockMvc.perform(get("/citas?desde=" + LocalDate.now(ZONA)).header("Authorization", bearer(medico)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/citas/999999").header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());
        intentarCancelar(999999, "No existe").andExpect(status().isNotFound());
    }
}
