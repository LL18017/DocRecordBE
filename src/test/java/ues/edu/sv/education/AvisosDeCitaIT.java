package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import ues.edu.sv.education.model.enums.EventCodeEnums;
import ues.edu.sv.education.service.EventProcessorService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Correos de las citas: confirmacion al agendar (HU-30 c4), recordatorio de 24
 * horas (HU-33) y aviso de cancelacion y reprogramacion (HU-32 c4).
 *
 * Los trabajos programados no arrancan solos en las pruebas (ver
 * PruebaDeIntegracion): cada prueba los llama a mano.
 */
class AvisosDeCitaIT extends PruebaClinica {

    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");

    @Autowired private EventProcessorService procesador;
    @Autowired private JdbcTemplate jdbc;

    private String medico;
    private String correoDelMedico;
    private int medicoId;
    private int sede;
    private int tipo;

    @BeforeEach
    void prepararLaAgenda() throws Exception {
        medico = tokenDeMedico();
        sede = crearSede(medico);

        String cuerpo = mockMvc.perform(post("/tipos-cita")
                        .header("Authorization", bearer(tokenDeAdministrador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Consulta general %d"}
                                """.formatted(siguiente())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        tipo = json.readTree(cuerpo).get("tipoCitaId").asInt();

        String medicos = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/citas/medicos?clinicaId=" + sede).header("Authorization", bearer(medico)))
                .andReturn().getResponse().getContentAsString();
        medicoId = json.readTree(medicos).get(0).get("medicoId").asInt();
        correoDelMedico = usuarios.findById(medicoId).orElseThrow().getEmail();
    }

    private long pacienteConCorreo(String correo) throws Exception {
        String cuerpo = mockMvc.perform(post("/pacientes")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"persona":{"dui":"%s","nombres":"Paciente","apellidos":"Con Correo",
                                            "fechaNacimiento":"1990-05-20","sexo":"F","email":"%s"},
                                 "tipoSangre":"O+"}
                                """.formatted(duiUnico(), correo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("personaId").asLong();
    }

    /** Pasado manana a la hora dada: lejos de la regla de las 4 horas. */
    private static LocalDateTime pasadoManana(int hora, int minuto) {
        return LocalDate.now(ZONA).plusDays(2).atTime(hora, minuto);
    }

    private int agendar(long pacienteId, LocalDateTime fechaHora) throws Exception {
        String cuerpo = mockMvc.perform(post("/citas")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"fechaHora":"%s","tipoCitaId":%d,"medicoId":%d,"clinicaId":%d}
                                """.formatted(pacienteId, fechaHora, tipo, medicoId, sede)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("citaId").asInt();
    }

    private void cancelar(int citaId, String motivo) throws Exception {
        mockMvc.perform(patch("/citas/" + citaId + "/cancelacion")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"motivo":"%s"}
                                """.formatted(motivo)))
                .andExpect(status().isOk());
    }

    private void reprogramar(int citaId, LocalDateTime fechaHora) throws Exception {
        mockMvc.perform(put("/citas/" + citaId)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fechaHora":"%s"}
                                """.formatted(fechaHora)))
                .andExpect(status().isOk());
    }

    private List<MimeMessage> correosPara(String destinatario) {
        ArgumentCaptor<MimeMessage> mensajes = ArgumentCaptor.forClass(MimeMessage.class);
        verify(correoDePruebas, atLeast(0)).send(mensajes.capture());
        return mensajes.getAllValues().stream()
                .filter(m -> destinatario.equals(CorreosDePrueba.destinatario(m)))
                .toList();
    }

    /** Lo que recibio el medico por el asunto dado: ya tiene el correo de su registro. */
    private List<MimeMessage> correosDelMedico(String asunto) {
        return correosPara(correoDelMedico).stream()
                .filter(m -> asunto.equals(CorreosDePrueba.asunto(m)))
                .toList();
    }

    private long eventos(int tipoDeEvento, int citaId) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM events WHERE event_type_id = ? AND ref = ?",
                Long.class, tipoDeEvento, String.valueOf(citaId));
        return total == null ? 0 : total;
    }

    private LocalDateTime marcaDeRecordatorio(int citaId) {
        return jdbc.queryForObject("SELECT recordatorio_enviado_en FROM citas WHERE cita_id = ?",
                LocalDateTime.class, citaId);
    }

    // ── HU-30 c4 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-30 c4: al agendar el paciente recibe una confirmacion propia, no el recordatorio")
    void agendarEnviaLaConfirmacion() throws Exception {
        String correo = correoUnico("paciente.confirma");
        agendar(pacienteConCorreo(correo), pasadoManana(9, 0));

        procesador.processAppoinmentEvents();

        List<MimeMessage> recibidos = correosPara(correo);
        assertEquals(1, recibidos.size());
        assertEquals("Tu cita médica quedó agendada en DocRecord Sv", CorreosDePrueba.asunto(recibidos.get(0)));
        String html = CorreosDePrueba.parteHtml(recibidos.get(0));
        assertTrue(html.contains("quedó agendada"), html);
        assertTrue(html.contains("Calle Principal"), "debe llevar la direccion de la sede");
        assertFalse(html.contains("Recordatorio"), "no es el recordatorio");
        assertTrue(CorreosDePrueba.parteDeTexto(recibidos.get(0)).contains("Calle Principal"));

        // Una sola vez: otra pasada no repite.
        procesador.processAppoinmentEvents();
        assertEquals(1, correosPara(correo).size());
    }

    // ── HU-32 c4 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-32 c4: cancelar avisa por correo al paciente y al medico, con el motivo")
    void cancelarAvisaAlPacienteYAlMedico() throws Exception {
        String correo = correoUnico("paciente.cancela");
        int citaId = agendar(pacienteConCorreo(correo), pasadoManana(10, 0));
        procesador.processAppoinmentEvents();

        cancelar(citaId, "El paciente viaja ese dia");
        procesador.processAppoinmentEvents();

        // El paciente tiene la confirmacion y la cancelacion; el medico, solo la cancelacion.
        List<MimeMessage> delPaciente = correosPara(correo);
        assertEquals(2, delPaciente.size());
        MimeMessage aviso = delPaciente.get(1);
        assertEquals("Se canceló una cita médica en DocRecord Sv", CorreosDePrueba.asunto(aviso));
        assertTrue(CorreosDePrueba.parteHtml(aviso).contains("El paciente viaja ese dia"));

        List<MimeMessage> delMedico = correosDelMedico("Se canceló una cita médica en DocRecord Sv");
        assertEquals(1, delMedico.size());
        assertEquals("Se canceló una cita médica en DocRecord Sv", CorreosDePrueba.asunto(delMedico.get(0)));
        assertTrue(CorreosDePrueba.parteDeTexto(delMedico.get(0)).contains("Paciente Con Correo"),
                "el medico ve de que paciente era la cita");
    }

    @Test
    @DisplayName("HU-32 c4: si el paciente no tiene correo, la cancelacion solo le llega al medico")
    void sinCorreoDelPacienteSoloSeAvisaAlMedico() throws Exception {
        int citaId = agendar(crearPaciente(medico), pasadoManana(10, 30));

        cancelar(citaId, "Imprevisto");
        procesador.processAppoinmentEvents();

        assertEquals(1, correosDelMedico("Se canceló una cita médica en DocRecord Sv").size());
    }

    @Test
    @DisplayName("HU-32 c4: reprogramar avisa al paciente y al medico con la fecha anterior y la nueva")
    void reprogramarAvisaAlPacienteYAlMedico() throws Exception {
        String correo = correoUnico("paciente.reprograma");
        int citaId = agendar(pacienteConCorreo(correo), pasadoManana(11, 0));
        procesador.processAppoinmentEvents();

        reprogramar(citaId, pasadoManana(16, 0));
        procesador.processAppoinmentEvents();

        List<MimeMessage> delPaciente = correosPara(correo);
        assertEquals(2, delPaciente.size());
        MimeMessage aviso = delPaciente.get(1);
        assertEquals("Se reprogramó una cita médica en DocRecord Sv", CorreosDePrueba.asunto(aviso));
        String texto = CorreosDePrueba.parteDeTexto(aviso);
        assertTrue(texto.contains("16:00"), texto);
        assertTrue(texto.contains("11:00"), "debe decir a que hora estaba antes: " + texto);

        assertEquals(1, correosDelMedico("Se reprogramó una cita médica en DocRecord Sv").size());
    }

    @Test
    @DisplayName("HU-32 c4: mandar la misma fecha no es una reprogramacion y no avisa a nadie")
    void laMismaFechaNoAvisa() throws Exception {
        int citaId = agendar(pacienteConCorreo(correoUnico("paciente.igual")), pasadoManana(12, 0));

        reprogramar(citaId, pasadoManana(12, 0));

        assertEquals(0, eventos(EventCodeEnums.APPOINTMENT_RESCHEDULED.getId(), citaId));
    }

    // ── HU-33 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-33 c1 y c2: el recordatorio sale una sola vez, unas 24 h antes, con todos los datos")
    void elRecordatorioSaleUnaSolaVez() throws Exception {
        String correo = correoUnico("paciente.recuerda");
        LocalDateTime fecha = pasadoManana(14, 0);
        int citaId = agendar(pacienteConCorreo(correo), fecha);
        procesador.processAppoinmentEvents();
        assertEquals(1, correosPara(correo).size(), "la confirmacion");

        // 30 horas antes: fuera de la ventana de 23 a 25 horas.
        procesador.recordarCitas(fecha.minusHours(30));
        assertEquals(1, correosPara(correo).size());
        assertNull(marcaDeRecordatorio(citaId));

        // 24 horas antes: sale.
        procesador.recordarCitas(fecha.minusHours(24));
        List<MimeMessage> recibidos = correosPara(correo);
        assertEquals(2, recibidos.size());
        assertNotNull(marcaDeRecordatorio(citaId));

        MimeMessage recordatorio = recibidos.get(1);
        String html = CorreosDePrueba.parteHtml(recordatorio);
        assertTrue(html.contains("14:00"), "la hora");
        assertTrue(html.contains("Medico Del Turno"), "el medico");
        assertTrue(html.contains("Sede "), "la clinica con su nombre");
        assertTrue(html.contains("Calle Principal"), "la direccion");
        assertTrue(html.contains("/agenda"), "el enlace a la aplicacion");
        assertTrue(CorreosDePrueba.parteDeTexto(recordatorio).contains("/agenda"));

        // Ni la hora siguiente ni el trabajo de nuevo lo repiten.
        procesador.recordarCitas(fecha.minusHours(24));
        procesador.recordarCitas(fecha.minusHours(23).minusMinutes(30));
        assertEquals(2, correosPara(correo).size());
    }

    @Test
    @DisplayName("HU-33 c3: una cita cancelada o atendida no recibe recordatorio")
    void canceladaOAtendidaNoSeRecuerda() throws Exception {
        String correoA = correoUnico("paciente.cancelada");
        String correoB = correoUnico("paciente.atendida");
        LocalDateTime fecha = pasadoManana(15, 0);
        int cancelada = agendar(pacienteConCorreo(correoA), fecha);
        cancelar(cancelada, "No puede asistir");

        // Otro medico no hace falta: la misma franja del medico quedo libre al cancelar.
        int atendida = agendar(pacienteConCorreo(correoB), fecha);
        mockMvc.perform(put("/citas/" + atendida)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ATENDIDA\"}"))
                .andExpect(status().isOk());

        procesador.recordarCitas(fecha.minusHours(24));

        assertEquals(0, correosPara(correoA).size());
        assertEquals(0, correosPara(correoB).size());
        assertNull(marcaDeRecordatorio(cancelada));
        assertNull(marcaDeRecordatorio(atendida));
    }

    @Test
    @DisplayName("HU-33: sin correo del paciente no hay recordatorio ni marca")
    void sinCorreoNoHayRecordatorio() throws Exception {
        LocalDateTime fecha = pasadoManana(15, 30);
        int citaId = agendar(crearPaciente(medico), fecha);

        procesador.recordarCitas(fecha.minusHours(24));

        assertNull(marcaDeRecordatorio(citaId));
    }

    @Test
    @DisplayName("HU-33: si el correo falla se devuelve la marca y la pasada siguiente lo reintenta")
    void unFalloDeCorreoNoPierdeElRecordatorio() throws Exception {
        String correo = correoUnico("paciente.reintento");
        LocalDateTime fecha = pasadoManana(16, 30);
        int citaId = agendar(pacienteConCorreo(correo), fecha);

        doThrow(new MailSendException("SMTP caido a proposito"))
                .when(correoDePruebas).send(any(MimeMessage.class));
        procesador.recordarCitas(fecha.minusHours(24));
        assertNull(marcaDeRecordatorio(citaId), "un envio fallido no debe dejar la cita como avisada");

        doNothing().when(correoDePruebas).send(any(MimeMessage.class));
        procesador.recordarCitas(fecha.minusHours(23));
        assertNotNull(marcaDeRecordatorio(citaId));
    }
}
