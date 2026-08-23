package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prescripciones (epica E7) a traves de la API real.
 *
 * Las reglas que se prueban son las que hacen de una receta un documento y no
 * una nota: que la firme quien la emite, que no exista vacia, y que no
 * sobreviva sin la consulta que la origino.
 */
class PrescripcionCrudIT extends PruebaClinica {

    @Autowired private JdbcTemplate jdbc;

    private String medico;
    private long paciente;
    private long consultaId;

    @BeforeEach
    void prepararUnaConsulta() throws Exception {
        medico = tokenDeMedico();
        paciente = crearPaciente(medico);
        consultaId = crearConsultaSimple(medico, paciente).get("consultaId").asLong();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Una receta sin medicamentos no es una receta
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("una receta con la lista de medicamentos vacia se rechaza y no se guarda")
    void unaRecetaVaciaSeRechaza() throws Exception {
        mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultaId\":%d,\"medicamentos\":[]}".formatted(consultaId)))
                .andExpect(status().isBadRequest());

        // Sin la lista siquiera: un cliente que se olvida del campo tampoco
        // puede emitir un papel firmado en blanco.
        mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultaId\":%d}".formatted(consultaId)))
                .andExpect(status().isBadRequest());

        // Un renglon sin nombre de medicamento no llena la receta: no indica
        // nada, solo ocupa lugar.
        mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consultaId":%d,"medicamentos":[{"dosis":"1 tableta"}]}
                                """.formatted(consultaId)))
                .andExpect(status().isBadRequest());

        assertEquals(0, recetasDeLaConsulta().size(),
                "ninguna de las peticiones rechazadas debio dejar una receta");
    }

    // ══════════════════════════════════════════════════════════════════════
    // La firma
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("la receta la firma el medico autenticado, no el que venga en el cuerpo")
    void laFirmaEsLaDelMedicoAutenticado() throws Exception {
        // La firma es lo que hace de la receta un documento con responsable.
        // Aceptarla del cuerpo permitiria recetar en nombre de otro medico.
        String otroMedico = tokenDeMedico();
        long idDelOtroMedico = crearConsultaSimple(otroMedico, paciente)
                .get("medico").get("personaId").asLong();

        JsonNode receta = emitir(medico, """
                {"consultaId":%d,"medicoId":%d,
                 "medicamentos":[{"medicamento":"Amoxicilina 500 mg","dosis":"1 tableta",
                                  "frecuencia":"Cada 8 horas","duracion":"7 dias"}]}
                """.formatted(consultaId, idDelOtroMedico));

        assertNotEquals(idDelOtroMedico, receta.get("medico").get("personaId").asLong(),
                "la receta quedo firmada por el medico que venia en el cuerpo");
    }

    @Test
    @DisplayName("la receta que firma un medico de guardia queda a su nombre, no al del que atendio")
    void laFirmaPuedeSerDeUnMedicoDistintoAlQueAtendio() throws Exception {
        // Es la razon de que prescripciones.medico_id exista aunque la consulta
        // ya tenga medico: en una clinica con turnos, quien receta no siempre
        // es quien abrio la consulta, y la firma debe decir la verdad.
        String medicoDeGuardia = tokenDeMedico();

        JsonNode receta = emitir(medicoDeGuardia, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Ibuprofeno 400 mg"}]}
                """.formatted(consultaId));

        long medicoQueAtendio = json.readTree(
                mockMvc.perform(get("/consultas/{id}", consultaId).header("Authorization", bearer(medico)))
                        .andReturn().getResponse().getContentAsString())
                .get("medico").get("personaId").asLong();

        assertNotEquals(medicoQueAtendio, receta.get("medico").get("personaId").asLong(),
                "la firma se dedujo de la consulta en vez de guardarse");
    }

    // ══════════════════════════════════════════════════════════════════════
    // Contenido
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("la receta guarda cada medicamento con su dosis, frecuencia y duracion")
    void laRecetaGuardaCadaRenglon() throws Exception {
        JsonNode receta = emitir(medico, """
                {"consultaId":%d,"medicamentos":[
                   {"medicamento":"Amoxicilina 500 mg","dosis":"1 tableta","frecuencia":"Cada 8 horas","duracion":"7 dias"},
                   {"medicamento":"Suspender el antibiotico anterior"}
                ]}
                """.formatted(consultaId));

        JsonNode renglones = receta.get("medicamentos");
        assertEquals(2, renglones.size());

        assertNotNull(renglones.get(0).get("id"));
        assertEquals("Amoxicilina 500 mg", renglones.get(0).get("medicamento").asText());
        assertEquals("Cada 8 horas", renglones.get(0).get("frecuencia").asText());
        assertEquals("7 dias", renglones.get(0).get("duracion").asText());

        // Una indicacion sin dosis es legitima y debe poder guardarse.
        assertEquals("Suspender el antibiotico anterior", renglones.get(1).get("medicamento").asText());
        assertTrue(renglones.get(1).get("dosis").isNull());

        assertEquals(consultaId, receta.get("consultaId").asLong());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Borrar la consulta se lleva sus recetas
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("borrar una consulta arrastra sus recetas y los medicamentos de estas")
    void borrarLaConsultaArrastraSusRecetas() throws Exception {
        long primera = emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Amoxicilina 500 mg"},
                                                 {"medicamento":"Paracetamol 500 mg"}]}
                """.formatted(consultaId)).get("prescripcionId").asLong();

        long segunda = emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Loratadina 10 mg"}]}
                """.formatted(consultaId)).get("prescripcionId").asLong();

        assertEquals(3, medicamentosGuardados(primera, segunda), "no se guardaron los renglones");

        mockMvc.perform(delete("/consultas/{id}", consultaId).header("Authorization", bearer(medico)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/prescripciones/{id}", primera).header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/prescripciones/{id}", segunda).header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());

        // Los renglones tambien: si solo cayeran las recetas, la tabla de
        // medicamentos quedaria con filas apuntando a recetas que ya no
        // existen. Se comprueba contra la base porque esa cascada la impone
        // PostgreSQL (V6), no el codigo Java.
        assertEquals(0, medicamentosGuardados(primera, segunda),
                "quedaron medicamentos huerfanos de una receta borrada");
    }

    // ══════════════════════════════════════════════════════════════════════
    // Listados
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("las recetas de un paciente salen todas, ordenadas y sin las de otro paciente")
    void listarPorPacienteDevuelveTodasSusRecetas() throws Exception {
        long otraConsulta = crearConsultaSimple(medico, paciente).get("consultaId").asLong();
        long otroPaciente = crearPaciente(medico);
        long consultaAjena = crearConsultaSimple(medico, otroPaciente).get("consultaId").asLong();

        long primera = emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Amoxicilina 500 mg"}]}
                """.formatted(consultaId)).get("prescripcionId").asLong();
        long segunda = emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Loratadina 10 mg"}]}
                """.formatted(otraConsulta)).get("prescripcionId").asLong();
        emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Receta de otro paciente"}]}
                """.formatted(consultaAjena));

        JsonNode delPaciente = json.readTree(
                mockMvc.perform(get("/prescripciones").param("pacienteId", String.valueOf(paciente))
                                .header("Authorization", bearer(medico)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());

        assertEquals(2, delPaciente.size(), "deben salir las recetas de TODAS sus consultas, y solo las suyas");
        assertEquals(segunda, delPaciente.get(0).get("prescripcionId").asLong(),
                "la mas reciente va primero");
        assertEquals(primera, delPaciente.get(1).get("prescripcionId").asLong());

        // Por consulta, solo la de esa consulta.
        JsonNode deLaConsulta = recetasDeLaConsulta();
        assertEquals(1, deLaConsulta.size());
        assertEquals(primera, deLaConsulta.get(0).get("prescripcionId").asLong());
    }

    @Test
    @DisplayName("listar recetas sin filtro se rechaza; con filtros inexistentes responde 404")
    void listarSinFiltroSeRechaza() throws Exception {
        // Sin filtro esto devolveria la medicacion de todos los pacientes del
        // sistema, que no responde a ninguna pregunta real.
        mockMvc.perform(get("/prescripciones").header("Authorization", bearer(medico)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/prescripciones").param("consultaId", "9999999")
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/prescripciones").param("pacienteId", "9999999")
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/prescripciones/{id}", 9999999)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("una receta sobre una consulta que no existe responde 404, no 500")
    void unaConsultaInexistenteResponde404() throws Exception {
        mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consultaId":9999999,"medicamentos":[{"medicamento":"Amoxicilina 500 mg"}]}
                                """))
                .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Quien puede que
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("la enfermera lee las recetas pero no las emite")
    void laEnfermeraLeePeroNoReceta() throws Exception {
        // Enfermeria es quien administra lo recetado: una receta que no puede
        // leer no sirve de nada. Firmarla es otra cosa.
        String enfermera = tokenDeEnfermera();

        emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Amoxicilina 500 mg"}]}
                """.formatted(consultaId));

        mockMvc.perform(get("/prescripciones").param("consultaId", String.valueOf(consultaId))
                        .header("Authorization", bearer(enfermera)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(enfermera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consultaId":%d,"medicamentos":[{"medicamento":"Amoxicilina 500 mg"}]}
                                """.formatted(consultaId)))
                .andExpect(status().isForbidden());

        assertEquals(1, recetasDeLaConsulta().size(), "la enfermera emitio una receta");
    }

    @Test
    @DisplayName("un administrador no puede recetar: no existe recetar en nombre de otro")
    void unAdministradorNoPuedeRecetar() throws Exception {
        String admin = tokenDeAdminQueNoEjerce();

        mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consultaId":%d,"medicamentos":[{"medicamento":"Amoxicilina 500 mg"}]}
                                """.formatted(consultaId)))
                .andExpect(status().isForbidden());

        assertEquals(0, recetasDeLaConsulta().size());
    }

    @Test
    @DisplayName("anular una receta la borra con todos sus medicamentos")
    void anularUnaRecetaLaBorraEntera() throws Exception {
        long recetaId = emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Amoxicilina 500 mg"},
                                                 {"medicamento":"Paracetamol 500 mg"}]}
                """.formatted(consultaId)).get("prescripcionId").asLong();

        mockMvc.perform(delete("/prescripciones/{id}", recetaId).header("Authorization", bearer(medico)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/prescripciones/{id}", recetaId).header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());

        // Media receta -- unos medicamentos si y otros no -- seria un documento
        // distinto del que firmo el medico.
        assertEquals(0, medicamentosGuardados(recetaId, recetaId));

        // La consulta sobrevive: la cascada va en un solo sentido.
        mockMvc.perform(get("/consultas/{id}", consultaId).header("Authorization", bearer(medico)))
                .andExpect(status().isOk());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    private JsonNode emitir(String token, String cuerpoJson) throws Exception {
        return json.readTree(mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode recetasDeLaConsulta() throws Exception {
        return json.readTree(mockMvc.perform(get("/prescripciones")
                        .param("consultaId", String.valueOf(consultaId))
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private int medicamentosGuardados(long unaReceta, long otraReceta) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM prescripcion_medicamentos WHERE prescripcion_id IN (?, ?)",
                Integer.class, unaReceta, otraReceta);
        return total == null ? 0 : total;
    }
}
