package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Linea de tiempo del expediente (HU-15, DRS-87): GET
 * /pacientes/{id}/linea-de-tiempo, y la sede de la toma de signos vitales que
 * hacia falta para el criterio 3 (V37).
 *
 * El montaje es el de un paciente atendido en DOS sedes: la consulta en una y
 * una toma anterior en otra. Es el caso que justifica la HU -el medico que lo
 * ve por primera vez entiende lo que paso en otra clinica de la red-, y el que
 * romperia un filtro por la sede activa.
 */
class LineaDeTiempoIT extends PruebaClinica {

    @Autowired private JdbcTemplate jdbc;

    private static final LocalDateTime FECHA_CONSULTA = LocalDateTime.of(2026, 3, 10, 9, 0);
    private static final LocalDateTime FECHA_TOMA_ANTIGUA = LocalDateTime.of(2026, 3, 1, 8, 0);

    private String medico;
    private String enfermera;
    private long paciente;
    private int sedeDeLaConsulta;
    private int otraSede;
    private long consultaId;
    private long recetaId;
    private long tomaAntiguaId;

    @BeforeEach
    void preparar() throws Exception {
        medico = tokenDeMedico();
        enfermera = tokenDeEnfermera();
        paciente = crearPaciente(medico);
        sedeDeLaConsulta = crearSede(medico);
        otraSede = crearSede(medico);

        tomaAntiguaId = cuerpo(tomar("""
                {"pacienteId":%d,"clinicaId":%d,"tomadoEn":"%s","pulsoLpm":80}
                """.formatted(paciente, otraSede, FECHA_TOMA_ANTIGUA)).andExpect(status().isCreated()))
                .get("signosVitalesId").asLong();

        consultaId = crearConsulta(medico, """
                {"pacienteId":%d,"clinicaId":%d,"fecha":"%s","motivo":"Tos persistente",
                 "diagnostico":"Bronquitis aguda"}
                """.formatted(paciente, sedeDeLaConsulta, FECHA_CONSULTA)).get("consultaId").asLong();

        long amoxicilina = jdbc.queryForObject(
                "SELECT medicamento_id FROM medicamentos WHERE nombre_comercial = 'Amoxil' AND presentacion = 'Cápsula'",
                Long.class);
        recetaId = cuerpo(mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consultaId":%d,"medicamentos":[{"medicamentoId":%d,"dosis":"1 cápsula",
                                 "viaAdministracion":"Oral","frecuencia":"Cada 8 horas","duracion":"7 dias"}]}
                                """.formatted(consultaId, amoxicilina)))
                .andExpect(status().isCreated())).get("prescripcionId").asLong();
    }

    @Test
    @DisplayName("HU-15 c1: consultas, tomas y recetas en una sola lista, de la mas reciente a la mas antigua")
    void unaSolaListaOrdenada() throws Exception {
        JsonNode eventos = lineaDeTiempo(medico);

        // La receta, la toma que crearConsulta hace de triage, la consulta y
        // la toma antigua.
        assertEquals(4, eventos.size());
        List<String> tipos = new ArrayList<>();
        eventos.forEach(e -> tipos.add(e.get("tipo").asText()));
        assertEquals(List.of("RECETA", "SIGNOS_VITALES", "CONSULTA", "SIGNOS_VITALES"), tipos);

        for (int i = 1; i < eventos.size(); i++) {
            LocalDateTime anterior = LocalDateTime.parse(eventos.get(i - 1).get("fecha").asText());
            LocalDateTime actual = LocalDateTime.parse(eventos.get(i).get("fecha").asText());
            assertFalse(actual.isAfter(anterior), "la linea de tiempo no esta de lo mas reciente a lo mas antiguo");
        }

        JsonNode consulta = eventos.get(2);
        assertEquals(consultaId, consulta.get("id").asLong());
        assertEquals(FECHA_CONSULTA, LocalDateTime.parse(consulta.get("fecha").asText()));
        assertEquals("Tos persistente", consulta.get("consulta").get("motivo").asText());
        assertTrue(consulta.get("signosVitales").isNull() && consulta.get("receta").isNull(),
                "un evento lleva solo el detalle de su tipo");

        assertEquals(recetaId, eventos.get(0).get("receta").get("prescripcionId").asLong());
        assertEquals(tomaAntiguaId, eventos.get(3).get("signosVitales").get("signosVitalesId").asLong());
    }

    @Test
    @DisplayName("HU-15 c3: cada evento dice en que clinica y que profesional lo registro, de cualquier sede")
    void cadaEventoDiceDondeYQuien() throws Exception {
        JsonNode eventos = lineaDeTiempo(medico);

        JsonNode consulta = evento(eventos, "CONSULTA", consultaId);
        assertEquals(sedeDeLaConsulta, consulta.get("clinica").get("clinicaId").asInt());
        assertEquals("MEDICO", consulta.get("profesional").get("rol").asText());
        assertEquals("Medico", consulta.get("profesional").get("nombres").asText());

        // La receta no tiene sede propia: es la de la consulta de la que sale.
        JsonNode receta = evento(eventos, "RECETA", recetaId);
        assertEquals(sedeDeLaConsulta, receta.get("clinica").get("clinicaId").asInt());
        assertEquals("MEDICO", receta.get("profesional").get("rol").asText());

        // La toma de la otra sede aparece igual: nada se filtra por sede.
        JsonNode toma = evento(eventos, "SIGNOS_VITALES", tomaAntiguaId);
        assertEquals(otraSede, toma.get("clinica").get("clinicaId").asInt());
        assertEquals("ENFERMERA", toma.get("profesional").get("rol").asText());
        assertEquals("Enfermera", toma.get("profesional").get("nombres").asText());
    }

    @Test
    @DisplayName("HU-15 c4: la enfermera ve los eventos pero la consulta le llega sin el diagnostico")
    void laEnfermeraNoVeElDiagnostico() throws Exception {
        JsonNode paraEnfermeria = evento(lineaDeTiempo(enfermera), "CONSULTA", consultaId);
        assertTrue(paraEnfermeria.get("consulta").get("diagnostico").isNull(),
                "el diagnostico no debe viajar a enfermeria");
        assertEquals("Tos persistente", paraEnfermeria.get("consulta").get("motivo").asText());
        assertEquals(4, lineaDeTiempo(enfermera).size(), "enfermeria ve todos los eventos");

        JsonNode paraElMedico = evento(lineaDeTiempo(medico), "CONSULTA", consultaId);
        assertEquals("Bronquitis aguda", paraElMedico.get("consulta").get("diagnostico").asText());
    }

    @Test
    @DisplayName("HU-15 c5: la linea de tiempo es de solo lectura")
    void esDeSoloLectura() throws Exception {
        String ruta = "/pacientes/{id}/linea-de-tiempo";
        mockMvc.perform(post(ruta, paciente).header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put(ruta, paciente).header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(ruta, paciente).header("Authorization", bearer(medico)))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("HU-15: lo anulado sigue en la linea de tiempo, marcado")
    void loAnuladoSigueMarcado() throws Exception {
        mockMvc.perform(post("/prescripciones/{id}/anular", recetaId)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Dosis mal escrita en la receta\"}"))
                .andExpect(status().isOk());

        JsonNode receta = evento(lineaDeTiempo(medico), "RECETA", recetaId);
        assertEquals("ANULADA", receta.get("estado").asText());
        assertEquals("Dosis mal escrita en la receta", receta.get("receta").get("motivoAnulacion").asText());
    }

    @Test
    @DisplayName("HU-15: 404 si el paciente no existe, 401 sin sesion")
    void pacienteInexistenteYSinSesion() throws Exception {
        mockMvc.perform(get("/pacientes/{id}/linea-de-tiempo", 987654321L).header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/pacientes/{id}/linea-de-tiempo", paciente))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("V37: la toma guarda su sede; sin sede toma la de su consulta; una sede que no existe da 404")
    void laTomaGuardaSuSede() throws Exception {
        JsonNode conSede = cuerpo(tomar("""
                {"pacienteId":%d,"clinicaId":%d,"pulsoLpm":70}
                """.formatted(paciente, otraSede)).andExpect(status().isCreated()));
        assertEquals(otraSede, conSede.get("clinica").get("clinicaId").asInt());

        JsonNode deLaConsulta = cuerpo(tomar("""
                {"pacienteId":%d,"consultaId":%d,"pulsoLpm":70}
                """.formatted(paciente, consultaId)).andExpect(status().isCreated()));
        assertEquals(sedeDeLaConsulta, deLaConsulta.get("clinica").get("clinicaId").asInt());

        JsonNode sinNada = cuerpo(tomar("""
                {"pacienteId":%d,"pulsoLpm":70}
                """.formatted(paciente)).andExpect(status().isCreated()));
        assertTrue(sinNada.get("clinica").isNull(), "sin sede ni consulta, la sede queda en null");

        tomar("""
                {"pacienteId":%d,"clinicaId":987654,"pulsoLpm":70}
                """.formatted(paciente)).andExpect(status().isNotFound());
    }

    private ResultActions tomar(String cuerpoJson) throws Exception {
        return mockMvc.perform(post("/signos-vitales")
                .header("Authorization", bearer(enfermera))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoJson));
    }

    private JsonNode lineaDeTiempo(String token) throws Exception {
        return cuerpo(mockMvc.perform(get("/pacientes/{id}/linea-de-tiempo", paciente)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()));
    }

    private static JsonNode evento(JsonNode eventos, String tipo, long id) {
        for (JsonNode e : eventos) {
            if (tipo.equals(e.get("tipo").asText()) && e.get("id").asLong() == id) return e;
        }
        throw new AssertionError("no aparece el evento " + tipo + " " + id);
    }

    private JsonNode cuerpo(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }
}
