package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-27 (DRS-95) · Baja y reactivación de una clínica.
 *
 * Criterio 3: una sede con citas futuras AGENDADAS no se puede dar de baja, y
 * el rechazo dice cuántas son y nombra las primeras. Criterio 4: una sede
 * inactiva se reactiva y vuelve al mapa con sus datos intactos.
 *
 * Los criterios 1 y 2 (editar, y que la baja conserve el historial) ya los
 * cubren ClinicaCrudIT y MapaDeClinicasIT.
 */
class BajaDeClinicaIT extends PruebaClinica {

    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");

    private String medico;
    private int medicoId;
    private int sede;
    private int tipo;

    @BeforeEach
    void prepararLaSede() throws Exception {
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

        String medicos = mockMvc.perform(get("/citas/medicos?clinicaId=" + sede)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        medicoId = json.readTree(medicos).get(0).get("medicoId").asInt();
    }

    /** Agenda una cita dentro de {@code dias} dias, a la hora dada, en la sede. */
    private int agendar(long pacienteId, int dias, int hora) throws Exception {
        String fecha = LocalDate.now(ZONA).plusDays(dias).atTime(hora, 0).toString();
        String cuerpo = mockMvc.perform(post("/citas")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"fechaHora":"%s","tipoCitaId":%d,"medicoId":%d,"clinicaId":%d}
                                """.formatted(pacienteId, fecha, tipo, medicoId, sede)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("citaId").asInt();
    }

    private ResultActions cambiarEstado(int clinicaId, String estado) throws Exception {
        return mockMvc.perform(patch("/clinics/{id}/estado", clinicaId)
                .header("Authorization", bearer(medico))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"" + estado + "\"}"));
    }

    private ResultActions cambiarEstado(String estado) throws Exception {
        return cambiarEstado(sede, estado);
    }

    private String estadoDeLaSede() throws Exception {
        String cuerpo = mockMvc.perform(get("/clinics/mias").header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode c : json.readTree(cuerpo)) {
            if (c.get("clinicaId").asInt() == sede) return c.get("estado").asText();
        }
        throw new AssertionError("la sede no aparece en /clinics/mias");
    }

    private JsonNode sedeEnElMapa() throws Exception {
        String cuerpo = mockMvc.perform(get("/clinics/publicas").param("departamento", "Santa Ana"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode c : json.readTree(cuerpo)) {
            if (c.get("clinicaId").asInt() == sede) return c;
        }
        return null;
    }

    @Test
    @DisplayName("c3: con citas futuras agendadas la baja (PATCH) se rechaza con 409 y las lista")
    void laBajaConCitasFuturasSeRechazaYLasLista() throws Exception {
        agendar(crearPaciente(medico), 2, 9);
        agendar(crearPaciente(medico), 3, 10);

        String respuesta = cambiarEstado("INACTIVA")
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        String mensaje = json.readTree(respuesta).get("message").asText();

        assertTrue(mensaje.contains("2 citas futuras agendadas"), mensaje);
        // Fecha, hora y paciente de la primera cita (la mas proxima).
        String fecha = LocalDate.now(ZONA).plusDays(2).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertTrue(mensaje.contains(fecha + " a las 09:00"), mensaje);
        assertTrue(mensaje.contains("Paciente Del Caso"), mensaje);
        assertTrue(mensaje.indexOf("09:00") < mensaje.indexOf("10:00"),
                "las citas se listan de la mas proxima a la mas lejana: " + mensaje);

        assertEquals("ACTIVA", estadoDeLaSede(), "el rechazo no debe tocar la sede");
    }

    @Test
    @DisplayName("c3: DELETE tambien se rechaza con 409 mientras haya citas futuras agendadas")
    void deleteConCitasFuturasSeRechaza() throws Exception {
        agendar(crearPaciente(medico), 2, 9);

        String respuesta = mockMvc.perform(delete("/clinics/{id}", sede)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        assertTrue(json.readTree(respuesta).get("message").asText().contains("1 cita futura agendada"));
        assertEquals("ACTIVA", estadoDeLaSede());
    }

    @Test
    @DisplayName("c3: el rechazo cuenta todas pero nombra solo las primeras")
    void elRechazoNombraSoloLasPrimeras() throws Exception {
        for (int hora = 8; hora <= 13; hora++) {
            agendar(crearPaciente(medico), 2, hora);
        }

        String respuesta = cambiarEstado("INACTIVA")
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        String mensaje = json.readTree(respuesta).get("message").asText();

        assertTrue(mensaje.contains("6 citas futuras agendadas"), mensaje);
        assertTrue(mensaje.contains("(y 1 más)"), mensaje);
        assertFalse(mensaje.contains("13:00"), "la sexta cita no se nombra: " + mensaje);
    }

    @Test
    @DisplayName("c3: una cita cancelada no impide la baja")
    void unaCitaCanceladaNoImpideLaBaja() throws Exception {
        int cita = agendar(crearPaciente(medico), 2, 9);

        mockMvc.perform(patch("/citas/" + cita + "/cancelacion")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"El paciente viaja\"}"))
                .andExpect(status().isOk());

        cambiarEstado("INACTIVA").andExpect(status().isOk());
        assertEquals("INACTIVA", estadoDeLaSede());
    }

    @Test
    @DisplayName("c3: una cita de esta sede no impide dar de baja OTRA")
    void unaCitaDeOtraSedeNoImpideLaBaja() throws Exception {
        agendar(crearPaciente(medico), 2, 9);
        int otraSede = crearSede(medico);

        cambiarEstado(otraSede, "INACTIVA").andExpect(status().isOk());
    }

    @Test
    @DisplayName("c3 y c4: sin citas la baja se aplica, y reactivar devuelve la sede al mapa con sus datos")
    void bajaSinCitasYReactivacion() throws Exception {
        JsonNode antes = sedeEnElMapa();
        assertNotNull(antes, "la sede recien creada debe estar en el mapa publico");

        cambiarEstado("INACTIVA").andExpect(status().isOk());
        assertNull(sedeEnElMapa(), "una sede dada de baja sale del mapa publico");

        cambiarEstado("ACTIVA").andExpect(status().isOk());
        JsonNode despues = sedeEnElMapa();
        assertNotNull(despues, "reactivada, la sede vuelve al mapa");
        assertEquals(antes.get("name").asText(), despues.get("name").asText());
        assertEquals(antes.get("direccion").asText(), despues.get("direccion").asText());
        assertEquals(antes.get("latitud").asDouble(), despues.get("latitud").asDouble(), 1e-9);
        assertEquals(antes.get("longitud").asDouble(), despues.get("longitud").asDouble(), 1e-9);
    }

    @Test
    @DisplayName("c4: dar de baja a la que ya esta de baja no falla, y reactivar siempre se puede")
    void bajaIdempotenteYReactivacion() throws Exception {
        cambiarEstado("INACTIVA").andExpect(status().isOk());
        cambiarEstado("INACTIVA").andExpect(status().isOk());
        cambiarEstado("ACTIVA").andExpect(status().isOk());
        assertEquals("ACTIVA", estadoDeLaSede());
    }
}
