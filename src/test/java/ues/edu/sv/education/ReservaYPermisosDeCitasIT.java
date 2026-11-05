package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.model.dto.cita.CitaRequestDto;
import ues.edu.sv.education.service.Cita.CitaService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-30 y HU-32 (DRS-29, DRS-99) con los criterios del 4 de noviembre de 2026:
 * la cita la agenda, cancela y reprograma el personal de la clinica (la
 * enfermera, o el medico) a nombre del paciente, y el paciente recibe 403.
 *
 * Lo demas de agendar y cancelar esta en AgendaDeCitasIT, con el medico como
 * quien opera. Aqui va lo que esa clase no cubria: que la enfermera tambien
 * puede, que el paciente no, y la reserva simultanea de verdad (criterio 5),
 * con dos hilos y sin el indice unico de V24.
 */
class ReservaYPermisosDeCitasIT extends PruebaClinica {

    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");
    private static final String INDICE = "citas_franja_del_medico_uq";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private CitaService citaService;

    private String medico;
    private String enfermera;
    private int medicoId;
    private int sede;
    private int tipo;

    @BeforeEach
    void prepararLaAgenda() throws Exception {
        medico = tokenDeMedico();
        enfermera = tokenDeEnfermera();
        sede = crearSede(medico);

        tipo = json.readTree(mockMvc.perform(post("/tipos-cita")
                        .header("Authorization", bearer(tokenDeAdministrador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Control %d\"}".formatted(siguiente())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("tipoCitaId").asInt();

        medicoId = json.readTree(mockMvc.perform(get("/citas/medicos?clinicaId=" + sede)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get(0).get("medicoId").asInt();
    }

    // ── El personal agenda y cancela ───────────────────────────────────────

    @Test
    @DisplayName("HU-30 c4 y HU-32 c1: la enfermera agenda la cita del paciente y la cancela con motivo")
    void laEnfermeraAgendaYCancela() throws Exception {
        long paciente = crearPaciente(medico);

        JsonNode cita = cuerpo(agendar(enfermera, paciente, pasadoManana(9, 0)).andExpect(status().isCreated()));
        assertEquals("AGENDADA", cita.get("estado").asText());

        JsonNode cancelada = cuerpo(mockMvc.perform(patch("/citas/" + cita.get("citaId").asInt() + "/cancelacion")
                        .header("Authorization", bearer(enfermera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"El paciente pidio otra fecha\"}"))
                .andExpect(status().isOk()));
        assertEquals("CANCELADA", cancelada.get("estado").asText());
    }

    // ── El paciente no ────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-30 c6: el paciente no puede agendarse una cita (403) y no se crea nada")
    void elPacienteNoAgenda() throws Exception {
        long paciente = crearPaciente(medico);
        String token = tokenDelPaciente(paciente);

        agendar(token, paciente, pasadoManana(10, 0)).andExpect(status().isForbidden());

        assertEquals(0, citasDe(paciente));
    }

    @Test
    @DisplayName("HU-32 c6: el paciente no puede cancelar ni reprogramar su cita (403) y la cita no cambia")
    void elPacienteNoCancelaNiReprograma() throws Exception {
        long paciente = crearPaciente(medico);
        String fecha = pasadoManana(11, 0);
        int citaId = cuerpo(agendar(enfermera, paciente, fecha).andExpect(status().isCreated())).get("citaId").asInt();
        String token = tokenDelPaciente(paciente);

        mockMvc.perform(patch("/citas/" + citaId + "/cancelacion")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"No podre asistir\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/citas/" + citaId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaHora\":\"%s\"}".formatted(pasadoManana(11, 30))))
                .andExpect(status().isForbidden());

        JsonNode cita = cuerpo(mockMvc.perform(get("/citas/" + citaId).header("Authorization", bearer(enfermera)))
                .andExpect(status().isOk()));
        assertEquals("AGENDADA", cita.get("estado").asText());
        assertEquals(fecha, cita.get("fechaHora").asText().substring(0, 16));
    }

    // ── Reserva simultanea ────────────────────────────────────────────────

    /**
     * Sin el indice unico, que es justo el caso de una base donde V24 no pudo
     * crearlo: solo queda el candado de CitaService. Se repite en varias
     * franjas porque una sola carrera puede salir bien por suerte; con dos
     * hilos que arrancan juntos y sin candado, alguna de ellas deja dos citas.
     */
    @Test
    @DisplayName("HU-30 c5: dos reservas simultaneas de la misma franja dejan una sola cita, aun sin el indice unico")
    void dosReservasALaVezDejanUnaSola() throws Exception {
        boolean habiaIndice = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = ?)", Boolean.class, INDICE));
        jdbc.execute("DROP INDEX IF EXISTS " + INDICE);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            for (int franja = 0; franja < 6; franja++) {
                String fecha = pasadoManana(8 + franja / 2, (franja % 2) * 30);
                long primero = crearPaciente(medico);
                long segundo = crearPaciente(medico);
                CyclicBarrier salida = new CyclicBarrier(2);

                List<Future<String>> resultados = new ArrayList<>();
                for (long paciente : List.of(primero, segundo)) {
                    resultados.add(hilos.submit(() -> {
                        salida.await(10, TimeUnit.SECONDS);
                        try {
                            citaService.crear(new CitaRequestDto(paciente, fecha, tipo, medicoId, sede));
                            return "201";
                        } catch (GeneralException e) {
                            return e.getErrorCode();
                        }
                    }));
                }
                List<String> codigos = new ArrayList<>();
                for (Future<String> r : resultados) {
                    codigos.add(r.get(30, TimeUnit.SECONDS));
                }

                assertEquals(1, codigos.stream().filter("201"::equals).count(), "franja " + fecha + ": " + codigos);
                assertEquals(1, codigos.stream().filter("409"::equals).count(), "franja " + fecha + ": " + codigos);
                assertEquals(1, jdbc.queryForObject(
                        "SELECT COUNT(*) FROM citas WHERE medico_user_id = ? AND fecha_hora = ?::timestamp AND estado <> 'CANCELADA'",
                        Integer.class, medicoId, fecha), "franja " + fecha + ": quedo mas de una cita");
            }
        } finally {
            hilos.shutdownNow();
            // Si la prueba fallo, quedaron dos citas vivas en una franja de
            // este medico y el indice no se podria volver a crear, lo que
            // taparia el motivo real. Se cancelan las que sobran (el medico es
            // de esta prueba) antes de reponerlo.
            jdbc.update("""
                    UPDATE citas SET estado = 'CANCELADA', motivo_cancelacion = 'Duplicada por la prueba'
                    WHERE medico_user_id = ? AND estado <> 'CANCELADA' AND cita_id NOT IN (
                        SELECT MIN(cita_id) FROM citas
                        WHERE medico_user_id = ? AND estado <> 'CANCELADA' GROUP BY fecha_hora)
                    """, medicoId, medicoId);
            if (habiaIndice) {
                jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + INDICE
                        + " ON public.citas (medico_user_id, fecha_hora) WHERE estado <> 'CANCELADA'");
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    /** Pasado mañana a la hora dada: lejos de la regla de las 4 horas. */
    private static String pasadoManana(int hora, int minuto) {
        return LocalDate.now(ZONA).plusDays(2).atTime(hora, minuto).toString();
    }

    private ResultActions agendar(String token, long pacienteId, String fechaHora) throws Exception {
        return mockMvc.perform(post("/citas")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pacienteId":%d,"fechaHora":"%s","tipoCitaId":%d,"medicoId":%d,"clinicaId":%d}
                        """.formatted(pacienteId, fechaHora, tipo, medicoId, sede)));
    }

    /** El camino real, como en PortalDelPacienteIT: acceso desde el expediente, invitacion canjeada, login. */
    private String tokenDelPaciente(long pacienteId) throws Exception {
        String correo = correoUnico("citas.paciente");
        mockMvc.perform(post("/pacientes/{id}/acceso-portal", pacienteId)
                        .header("Authorization", bearer(enfermera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(correo)))
                .andExpect(status().isOk());
        String invitacion = jdbc.queryForObject("""
                select i.token from invitaciones i join users u on u.user_id = i.user_id
                where lower(u.email) = lower(?) and i.usada = false
                """, String.class, correo);
        mockMvc.perform(post("/auth/invitacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(invitacion, CLAVE)))
                .andExpect(status().isNoContent());
        return iniciarSesion(correo);
    }

    private int citasDe(long pacienteId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM citas WHERE paciente_persona_id = ?", Integer.class, pacienteId);
    }

    private JsonNode cuerpo(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }
}
