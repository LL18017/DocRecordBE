package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Observaciones de la tutora: el telefono es salvadoreno de 8 digitos (con o
 * sin guion) y la fecha de nacimiento no puede ser posterior a hoy. Se prueba
 * en el alta y en la edicion del paciente.
 */
class TelefonoYNacimientoDelPacienteIT extends PruebaClinica {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/El_Salvador"));

    private ResultActions alta(String token, String telefono, LocalDate nacimiento) throws Exception {
        String tel = telefono == null ? "" : ",\"telefono\":\"" + telefono + "\"";
        return mockMvc.perform(post("/pacientes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"persona":{"dui":"%s","nombres":"Paciente","apellidos":"Telefono %d",
                                    "fechaNacimiento":"%s","sexo":"F"%s},
                         "tipoSangre":"O+"}
                        """.formatted(duiUnico(), siguiente(), nacimiento, tel)));
    }

    private ResultActions edicion(String token, long id, String persona) throws Exception {
        return mockMvc.perform(put("/pacientes/{id}", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"persona\":" + persona + "}"));
    }

    private long idDe(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("personaId").asLong();
    }

    // ── Telefono ──────────────────────────────────────────────────────────

    @ParameterizedTest(name = "el alta acepta {0} y lo guarda como 2222-2222")
    @ValueSource(strings = {"2222-2222", "22222222"})
    @DisplayName("el alta acepta 8 digitos con o sin guion y los guarda igual")
    void altaAceptaOchoDigitos(String telefono) throws Exception {
        String token = tokenDeMedico();
        String cuerpo = alta(token, telefono, LocalDate.of(1990, 5, 20))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertEquals("2222-2222", json.readTree(cuerpo).get("persona").get("telefono").asText());
    }

    @ParameterizedTest(name = "el alta rechaza {0}")
    @ValueSource(strings = {"222222222", "2222-22222", "1234567", "abcd-efgh", "+503 2222 2222"})
    @DisplayName("el alta rechaza todo lo que no sean 8 digitos, con un mensaje del campo")
    void altaRechazaOtrosFormatos(String telefono) throws Exception {
        String token = tokenDeMedico();
        String cuerpo = alta(token, telefono, LocalDate.of(1990, 5, 20))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertTrue(json.readTree(cuerpo).get("message").asText().contains("8 digitos"), cuerpo);
    }

    @Test
    @DisplayName("el telefono sigue siendo opcional")
    void telefonoOpcional() throws Exception {
        alta(tokenDeMedico(), null, LocalDate.of(1990, 5, 20)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("la edicion aplica la misma regla de telefono")
    void edicionValidaElTelefono() throws Exception {
        String token = tokenDeMedico();
        long id = idDe(alta(token, "2222-2222", LocalDate.of(1990, 5, 20)).andExpect(status().isCreated()));

        edicion(token, id, "{\"telefono\":\"999999999\"}").andExpect(status().isBadRequest());

        String cuerpo = edicion(token, id, "{\"telefono\":\"77778888\"}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals("7777-8888", json.readTree(cuerpo).get("persona").get("telefono").asText());
    }

    // ── Fecha de nacimiento ───────────────────────────────────────────────

    @Test
    @DisplayName("el alta rechaza una fecha de nacimiento posterior a hoy")
    void altaRechazaFechaFutura() throws Exception {
        String token = tokenDeMedico();
        String cuerpo = alta(token, null, HOY.plusDays(1))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        JsonNode respuesta = json.readTree(cuerpo);
        assertTrue(respuesta.get("message").asText().contains("posterior a hoy"), cuerpo);
    }

    @Test
    @DisplayName("nacer hoy es valido")
    void altaAceptaHoy() throws Exception {
        alta(tokenDeMedico(), null, HOY).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("la edicion rechaza una fecha de nacimiento futura")
    void edicionRechazaFechaFutura() throws Exception {
        String token = tokenDeMedico();
        long id = idDe(alta(token, null, LocalDate.of(1990, 5, 20)).andExpect(status().isCreated()));

        edicion(token, id, "{\"fechaNacimiento\":\"%s\"}".formatted(HOY.plusYears(1)))
                .andExpect(status().isBadRequest());

        edicion(token, id, "{\"fechaNacimiento\":\"%s\"}".formatted(HOY.minusYears(5)))
                .andExpect(status().isOk());
    }
}
