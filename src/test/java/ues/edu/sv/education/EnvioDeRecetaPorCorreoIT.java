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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-25 · Envio de la receta al paciente por correo (DRS-94).
 *
 * El envio lo hace EventProcessorService.processPreescripcionEvents, que en las
 * pruebas no arranca solo (ver PruebaDeIntegracion): cada prueba lo llama a
 * mano, que es ademas la unica forma de saber cuando termino.
 */
class EnvioDeRecetaPorCorreoIT extends PruebaClinica {

    @Autowired private EventProcessorService procesador;
    @Autowired private JdbcTemplate jdbc;

    private String medico;
    private long medicamento;

    @BeforeEach
    void prepararElMedico() throws Exception {
        medico = tokenDeMedico();
        medicamento = jdbc.queryForObject(
                "SELECT medicamento_id FROM medicamentos WHERE nombre_comercial = 'Panadol' AND presentacion = 'Tableta'",
                Long.class);
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

    private long consultaConDiagnostico(long pacienteId) throws Exception {
        return crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Fiebre","diagnostico":"Dengue clasico confidencial"}
                """.formatted(pacienteId)).get("consultaId").asLong();
    }

    private JsonNode emitir(long consultaId) throws Exception {
        return json.readTree(mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consultaId":%d,"medicamentos":[{"medicamentoId":%d,"dosis":"1 tableta","viaAdministracion":"Oral","frecuencia":"Cada 8 horas","duracion":"5 dias"}]}
                                """.formatted(consultaId, medicamento)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode recetaDelListado(long consultaId) throws Exception {
        String cuerpo = mockMvc.perform(get("/prescripciones").param("consultaId", String.valueOf(consultaId))
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("contenido").get(0);
    }

    private long eventosDeEnvio(long consultaId) {
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM events WHERE event_type_id = ? AND ref = ?",
                Long.class, EventCodeEnums.PREESCRIPTION_SEND.getId(), String.valueOf(consultaId));
        return total == null ? 0 : total;
    }

    private List<MimeMessage> correosPara(String destinatario) {
        ArgumentCaptor<MimeMessage> mensajes = ArgumentCaptor.forClass(MimeMessage.class);
        verify(correoDePruebas, atLeast(0)).send(mensajes.capture());
        return mensajes.getAllValues().stream()
                .filter(m -> destinatario.equals(CorreosDePrueba.destinatario(m)))
                .toList();
    }

    @Test
    @DisplayName("HU-25 c1: al emitir la receta se encola el envio y el procesador la manda al correo del paciente")
    void laRecetaSaleSolaAlCorreoDelPaciente() throws Exception {
        String correo = correoUnico("paciente.receta");
        long consultaId = consultaConDiagnostico(pacienteConCorreo(correo));

        JsonNode receta = emitir(consultaId);
        assertEquals("PENDIENTE", receta.get("envioPorCorreo").asText());
        assertEquals(1, eventosDeEnvio(consultaId));
        assertEquals(0, correosPara(correo).size(), "el correo no sale dentro de la peticion del medico");

        procesador.processPreescripcionEvents();

        assertEquals(1, correosPara(correo).size());
        assertEquals("ENVIADO", recetaDelListado(consultaId).get("envioPorCorreo").asText());
        assertNotNull(recetaDelListado(consultaId).get("envioPorCorreoFecha").asText());
    }

    @Test
    @DisplayName("HU-25 c3: el correo lleva paciente, fecha, medico y receta, pero no el texto del diagnostico")
    void elCorreoNoLlevaElDiagnostico() throws Exception {
        String correo = correoUnico("paciente.privacidad");
        long consultaId = consultaConDiagnostico(pacienteConCorreo(correo));
        emitir(consultaId);

        procesador.processPreescripcionEvents();

        MimeMessage mensaje = correosPara(correo).get(0);
        String html = CorreosDePrueba.parteHtml(mensaje);
        String texto = CorreosDePrueba.parteDeTexto(mensaje);
        assertTrue(html.contains("Paciente"), "debe llevar el nombre del paciente");
        assertTrue(html.contains("Panadol") || html.contains("Paracetamol"), "debe llevar la receta");
        assertFalse(html.contains("Dengue") || texto.contains("Dengue"), "el diagnostico no debe viajar por correo");
    }

    @Test
    @DisplayName("HU-25 c2: sin correo no se encola un envio vacio; la respuesta lo dice y el reenvio manual es 422")
    void sinCorreoSeAvisaYNoSeEncola() throws Exception {
        long consultaId = consultaConDiagnostico(crearPaciente(medico));

        JsonNode receta = emitir(consultaId);

        assertEquals("SIN_CORREO", receta.get("envioPorCorreo").asText());
        assertEquals(0, eventosDeEnvio(consultaId), "no debe quedar un envio sin destinatario");
        assertEquals("SIN_CORREO", recetaDelListado(consultaId).get("envioPorCorreo").asText());

        mockMvc.perform(post("/prescripciones/enviar-receta/{id}", consultaId)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isUnprocessableEntity());
        assertEquals(0, eventosDeEnvio(consultaId));
    }

    @Test
    @DisplayName("HU-25 c4: si el envio falla queda FALLIDO y visible, y el reenvio manual lo deja ENVIADO")
    void unEnvioFallidoSeVeYSePuedeReenviar() throws Exception {
        String correo = correoUnico("paciente.fallo");
        long consultaId = consultaConDiagnostico(pacienteConCorreo(correo));
        emitir(consultaId);

        doThrow(new MailSendException("SMTP caido a proposito para esta prueba"))
                .when(correoDePruebas).send(any(MimeMessage.class));
        procesador.processPreescripcionEvents();

        JsonNode fallida = recetaDelListado(consultaId);
        assertEquals("FALLIDO", fallida.get("envioPorCorreo").asText());
        assertNotNull(fallida.get("envioPorCorreoFecha"));

        // No se reintenta solo en bucle: otra pasada no lo toca.
        procesador.processPreescripcionEvents();
        assertEquals(1, eventosDeEnvio(consultaId));

        // El SMTP vuelve y el medico reenvia a mano.
        org.mockito.Mockito.doNothing().when(correoDePruebas).send(any(MimeMessage.class));
        mockMvc.perform(post("/prescripciones/enviar-receta/{id}", consultaId)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isNoContent());
        assertEquals("PENDIENTE", recetaDelListado(consultaId).get("envioPorCorreo").asText());

        procesador.processPreescripcionEvents();
        assertEquals("ENVIADO", recetaDelListado(consultaId).get("envioPorCorreo").asText());
    }

    @Test
    @DisplayName("una receta anterior al envio automatico, de un paciente con correo, no inventa un estado")
    void unaRecetaSinEnvioNoInventaEstado() throws Exception {
        String correo = correoUnico("paciente.antiguo");
        long consultaId = consultaConDiagnostico(pacienteConCorreo(correo));
        emitir(consultaId);
        jdbc.update("DELETE FROM events WHERE event_type_id = ? AND ref = ?",
                EventCodeEnums.PREESCRIPTION_SEND.getId(), String.valueOf(consultaId));

        assertTrue(recetaDelListado(consultaId).get("envioPorCorreo").isNull());
        assertNull(recetaDelListado(consultaId).get("envioPorCorreo").textValue());
    }
}
