package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-16 (DRS-26) · Criterio 4: una toma no se elimina, se anula con motivo.
 *
 *   "dado una toma ya registrada, cuando intento eliminarla, entonces el
 *    sistema no lo permite: solo puedo anularla dejando constancia del motivo"
 *
 * Lo que se vigila: que NO exista el borrado, que la anulación deje el motivo,
 * la autora y la hora, que la toma anulada siga visible, y que deje de contar
 * como "última" y como triage que habilita una consulta.
 */
class AnulacionDeSignosVitalesIT extends PruebaClinica {

    private String medico;
    private String enfermera;
    private long paciente;

    @BeforeEach
    void montar() throws Exception {
        medico = tokenDeMedico();
        enfermera = tokenDeEnfermera();
        paciente = crearPaciente(medico);
    }

    private JsonNode tomar(String token, String medidas) throws Exception {
        String cuerpo = mockMvc.perform(post("/signos-vitales")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d,%s}".formatted(paciente, medidas)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private org.springframework.test.web.servlet.ResultActions anular(
            String token, long id, String cuerpo) throws Exception {
        return mockMvc.perform(patch("/signos-vitales/{id}/anulacion", id)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private JsonNode leer(long id) throws Exception {
        return json.readTree(mockMvc.perform(get("/signos-vitales/{id}", id)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    // ══════════════════════════════════════════════════════════════════════
    // La anulación deja constancia
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("una toma nace ACTIVA, sin rastro de anulación")
    void nacenActivas() throws Exception {
        JsonNode toma = tomar(enfermera, "\"pulsoLpm\":72");

        assertEquals("ACTIVA", toma.get("estado").asText());
        assertTrue(toma.get("motivoAnulacion").isNull());
        assertTrue(toma.get("anuladaEn").isNull());
        assertTrue(toma.get("anuladaPor").isNull());
    }

    @Test
    @DisplayName("anular deja el estado, el motivo, quién y cuándo")
    void anularDejaConstancia() throws Exception {
        long id = tomar(enfermera, "\"pesoKg\":7.25").get("signosVitalesId").asLong();

        String cuerpo = anular(enfermera, id, "{\"motivo\":\"  Se corrió la coma: era 72.5 kg  \"}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode anulada = json.readTree(cuerpo);

        assertEquals("ANULADA", anulada.get("estado").asText());
        assertEquals("Se corrió la coma: era 72.5 kg", anulada.get("motivoAnulacion").asText(),
                "el motivo se guarda sin los espacios de los extremos");
        assertTrue(anulada.hasNonNull("anuladaEn"), "queda la hora de la anulación");
        assertEquals("Enfermera", anulada.get("anuladaPor").get("nombres").asText(),
                "queda quién la anuló");
        // Y lo demás de la toma no se toca: es el registro de lo que se midió.
        assertEquals("7.25", anulada.get("pesoKg").asText());
    }

    @Test
    @DisplayName("la toma anulada sigue visible, marcada, en la consulta y en el listado")
    void laAnuladaSigueVisible() throws Exception {
        long id = tomar(enfermera, "\"pulsoLpm\":72").get("signosVitalesId").asLong();
        anular(enfermera, id, "{\"motivo\":\"Paciente equivocado\"}").andExpect(status().isOk());

        assertEquals("ANULADA", leer(id).get("estado").asText());

        JsonNode pagina = json.readTree(mockMvc.perform(get("/signos-vitales")
                        .param("pacienteId", String.valueOf(paciente))
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode contenido = pagina.get("contenido");
        assertEquals(1, contenido.size(), "la anulada sigue en el histórico");
        assertEquals("ANULADA", contenido.get(0).get("estado").asText());
    }

    // ══════════════════════════════════════════════════════════════════════
    // No se elimina
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("eliminar una toma no se permite: no hay DELETE y la toma sigue ahí")
    void noSePuedeEliminar() throws Exception {
        long id = tomar(enfermera, "\"pulsoLpm\":72").get("signosVitalesId").asLong();

        mockMvc.perform(delete("/signos-vitales/{id}", id)
                        .header("Authorization", bearer(enfermera)))
                .andExpect(status().isMethodNotAllowed());

        assertEquals("ACTIVA", leer(id).get("estado").asText());
    }

    // ══════════════════════════════════════════════════════════════════════
    // El motivo es obligatorio
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("sin motivo, o con un motivo en blanco, se rechaza y la toma sigue activa")
    void exigeMotivo() throws Exception {
        long id = tomar(enfermera, "\"pulsoLpm\":72").get("signosVitalesId").asLong();

        anular(enfermera, id, "{}").andExpect(status().isBadRequest());
        anular(enfermera, id, "{\"motivo\":\"   \"}").andExpect(status().isBadRequest());
        anular(enfermera, id, "{\"motivo\":\"%s\"}".formatted("x".repeat(501)))
                .andExpect(status().isBadRequest());

        assertEquals("ACTIVA", leer(id).get("estado").asText());
    }

    @Test
    @DisplayName("anular una toma que no existe responde 404")
    void tomaInexistente() throws Exception {
        anular(enfermera, 999_999L, "{\"motivo\":\"No existe\"}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("anular dos veces responde 409 y conserva el primer motivo")
    void noSeAnulaDosVeces() throws Exception {
        long id = tomar(enfermera, "\"pulsoLpm\":72").get("signosVitalesId").asLong();
        anular(enfermera, id, "{\"motivo\":\"Primer motivo\"}").andExpect(status().isOk());

        anular(enfermera, id, "{\"motivo\":\"Segundo motivo\"}").andExpect(status().isConflict());

        assertEquals("Primer motivo", leer(id).get("motivoAnulacion").asText(),
                "la segunda anulación no pisa la huella de la primera");
    }

    // ══════════════════════════════════════════════════════════════════════
    // Quién puede anular
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("otra enfermera no puede anular una toma ajena")
    void soloLaAutora() throws Exception {
        long id = tomar(enfermera, "\"pulsoLpm\":72").get("signosVitalesId").asLong();

        anular(tokenDeEnfermera(), id, "{\"motivo\":\"No es mía\"}")
                .andExpect(status().isForbidden());

        assertEquals("ACTIVA", leer(id).get("estado").asText());
    }

    @Test
    @DisplayName("el médico y el administrador leen pero no anulan")
    void medicoYAdministradorNoAnulan() throws Exception {
        long id = tomar(enfermera, "\"pulsoLpm\":72").get("signosVitalesId").asLong();

        anular(medico, id, "{\"motivo\":\"Intento\"}").andExpect(status().isForbidden());
        anular(tokenDeAdministrador(), id, "{\"motivo\":\"Intento\"}").andExpect(status().isForbidden());

        assertEquals("ACTIVA", leer(id).get("estado").asText());
    }

    @Test
    @DisplayName("sin sesión no se anula")
    void sinSesion() throws Exception {
        mockMvc.perform(patch("/signos-vitales/{id}/anulacion", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Sin sesión\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Una anulada no cuenta
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("una toma anulada no cuenta como «última»: la última es la anterior válida")
    void laAnuladaNoEsLaUltima() throws Exception {
        long primera = tomar(enfermera, "\"pulsoLpm\":70,\"tomadoEn\":\"2026-09-13T08:00:00\"")
                .get("signosVitalesId").asLong();
        long segunda = tomar(enfermera, "\"pulsoLpm\":150,\"tomadoEn\":\"2026-09-13T09:00:00\"")
                .get("signosVitalesId").asLong();

        assertEquals(segunda, ultima().get("signosVitalesId").asLong());

        anular(enfermera, segunda, "{\"motivo\":\"El oxímetro estaba mal puesto\"}")
                .andExpect(status().isOk());

        assertEquals(primera, ultima().get("signosVitalesId").asLong(),
                "al anular la más reciente, la última pasa a ser la anterior");
    }

    @Test
    @DisplayName("si todas las tomas están anuladas, el paciente queda sin «última» (204)")
    void todasAnuladasNoHayUltima() throws Exception {
        long id = tomar(enfermera, "\"pulsoLpm\":72").get("signosVitalesId").asLong();
        anular(enfermera, id, "{\"motivo\":\"Paciente equivocado\"}").andExpect(status().isOk());

        mockMvc.perform(get("/signos-vitales/ultima")
                        .param("pacienteId", String.valueOf(paciente))
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("una toma anulada no habilita la consulta: hay que volver a tomar")
    void laAnuladaNoHabilitaLaConsulta() throws Exception {
        long id = tomar(enfermera, "\"pulsoLpm\":72").get("signosVitalesId").asLong();
        anular(enfermera, id, "{\"motivo\":\"Paciente equivocado\"}").andExpect(status().isOk());

        String respuesta = mockMvc.perform(post("/consultas")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d,\"motivo\":\"Control\",\"diagnostico\":\"Sano\"}".formatted(paciente)))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(respuesta.contains("signos vitales"),
                "el 409 es por falta de constantes válidas, y dijo: " + respuesta);

        // Con una toma nueva y válida, la consulta se abre.
        tomar(enfermera, "\"pulsoLpm\":74");
        mockMvc.perform(post("/consultas")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d,\"motivo\":\"Control\",\"diagnostico\":\"Sano\"}".formatted(paciente)))
                .andExpect(status().isCreated());
    }

    private JsonNode ultima() throws Exception {
        return json.readTree(mockMvc.perform(get("/signos-vitales/ultima")
                        .param("pacienteId", String.valueOf(paciente))
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
