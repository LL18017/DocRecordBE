package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-29 · Asignacion de medicos a una clinica con dias y horarios (DRS-97):
 * una prueba por criterio, mas lo que la agenda (HU-30) toma de los horarios.
 */
class HorariosDeMedicosIT extends PruebaClinica {

    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");
    private static final int LUNES = 1;

    private String admin;
    private String medico;
    private int medicoId;
    private int sedeA;
    private int sedeB;

    @BeforeEach
    void prepararDosSedes() throws Exception {
        admin = tokenDeAdministrador();
        medico = tokenDeMedico();
        sedeA = crearSede(medico);
        sedeB = crearSede(admin);
        medicoId = medicos(sedeA).get(0).get("medicoId").asInt();
    }

    private JsonNode leer(String ruta, String token) throws Exception {
        String cuerpo = mockMvc.perform(get(ruta).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private JsonNode medicos(int sede) throws Exception {
        return leer("/clinics/" + sede + "/medicos", admin);
    }

    private ResultActions agregarHorario(String token, int sede, int dia, String inicio, String fin) throws Exception {
        return mockMvc.perform(post("/clinics/" + sede + "/medicos/" + medicoId + "/horarios")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"diaSemana":%d,"horaInicio":"%s","horaFin":"%s"}
                        """.formatted(dia, inicio, fin)));
    }

    private static LocalDate proximoLunes() {
        return LocalDate.now(ZONA).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    @Test
    @DisplayName("c1: un medico atiende en dos sedes si los horarios no se traslapan, y queda asignado a la segunda")
    void dosSedesSinTraslape() throws Exception {
        agregarHorario(admin, sedeA, LUNES, "08:00", "12:00").andExpect(status().isCreated());
        agregarHorario(admin, sedeB, LUNES, "13:00", "17:00").andExpect(status().isCreated());

        assertEquals(medicoId, medicos(sedeB).get(0).get("medicoId").asInt());
    }

    @Test
    @DisplayName("c2: un traslape con otra sede se rechaza con 409 nombrando el conflicto")
    void elTraslapeSeRechaza() throws Exception {
        agregarHorario(admin, sedeA, LUNES, "08:00", "12:00").andExpect(status().isCreated());

        String cuerpo = agregarHorario(admin, sedeB, LUNES, "11:00", "14:00")
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        String mensaje = json.readTree(cuerpo).get("message").asText();
        assertTrue(mensaje.contains("08:00") && mensaje.contains("12:00"), mensaje);

        // Horas mal formadas o al reves: 400.
        agregarHorario(admin, sedeB, LUNES, "14:00", "13:00").andExpect(status().isBadRequest());
        agregarHorario(admin, sedeB, LUNES, "13:15", "14:00").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("c3: la ficha de la clinica lista sus medicos con especialidad y horario")
    void laFichaListaMedicosConHorario() throws Exception {
        agregarHorario(admin, sedeA, LUNES, "08:00", "12:00").andExpect(status().isCreated());

        JsonNode medico = medicos(sedeA).get(0);
        assertFalse(medico.get("especialidad").isNull());
        assertEquals(1, medico.get("horarios").size());
        assertEquals("08:00", medico.get("horarios").get(0).get("horaInicio").asText());
        assertEquals(LUNES, medico.get("horarios").get(0).get("diaSemana").asInt());
    }

    @Test
    @DisplayName("c4: antes de quitar la asignacion se pueden ver sus citas futuras; quitarla borra sus horarios ahi")
    void citasFuturasAntesDeQuitar() throws Exception {
        int sedeC = crearSede(admin);
        agregarHorario(admin, sedeB, LUNES, "08:00", "12:00").andExpect(status().isCreated());
        agregarHorario(admin, sedeC, LUNES, "13:00", "17:00").andExpect(status().isCreated());

        long paciente = crearPaciente(medico);
        int tipo = json.readTree(mockMvc.perform(post("/tipos-cita")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Control %d\"}".formatted(siguiente())))
                .andReturn().getResponse().getContentAsString()).get("tipoCitaId").asInt();
        mockMvc.perform(post("/citas")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"fechaHora":"%sT09:00","tipoCitaId":%d,"medicoId":%d,"clinicaId":%d}
                                """.formatted(paciente, proximoLunes(), tipo, medicoId, sedeB)))
                .andExpect(status().isCreated());

        JsonNode futuras = leer("/clinics/" + sedeB + "/medicos/" + medicoId + "/citas-futuras", admin);
        assertEquals(1, futuras.size());

        mockMvc.perform(delete("/clinics/" + sedeB + "/medicos/" + medicoId).header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        assertEquals(0, medicos(sedeB).size());
    }

    @Test
    @DisplayName("c5: quien no es administrador recibe 403 al modificar una asignacion")
    void soloElAdministrador() throws Exception {
        agregarHorario(medico, sedeA, LUNES, "08:00", "12:00").andExpect(status().isForbidden());
        mockMvc.perform(delete("/clinics/" + sedeA + "/medicos/" + medicoId).header("Authorization", bearer(medico)))
                .andExpect(status().isForbidden());

        // Leer la ficha si puede.
        leer("/clinics/" + sedeA + "/medicos", medico);
    }

    @Test
    @DisplayName("HU-30 c1 y c3: la agenda solo ofrece y solo acepta horas dentro del horario del medico")
    void laAgendaRespetaElHorario() throws Exception {
        agregarHorario(admin, sedeA, LUNES, "08:00", "10:00").andExpect(status().isCreated());
        LocalDate lunes = proximoLunes();

        JsonNode franjas = leer("/citas/franjas?clinicaId=%d&medicoId=%d&fecha=%s"
                .formatted(sedeA, medicoId, lunes), medico);
        assertEquals("[\"08:00\",\"08:30\",\"09:00\",\"09:30\"]", franjas.toString());

        long paciente = crearPaciente(medico);
        int tipo = json.readTree(mockMvc.perform(post("/tipos-cita")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Control %d\"}".formatted(siguiente())))
                .andReturn().getResponse().getContentAsString()).get("tipoCitaId").asInt();

        mockMvc.perform(post("/citas")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"fechaHora":"%sT14:00","tipoCitaId":%d,"medicoId":%d,"clinicaId":%d}
                                """.formatted(paciente, lunes, tipo, medicoId, sedeA)))
                .andExpect(status().isBadRequest());

        // Otro dia de la semana: ese lunes no aplica y no hay franjas.
        JsonNode martes = leer("/citas/franjas?clinicaId=%d&medicoId=%d&fecha=%s"
                .formatted(sedeA, medicoId, lunes.plusDays(1)), medico);
        assertEquals(0, martes.size());
    }
}
