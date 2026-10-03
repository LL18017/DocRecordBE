package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-12 (antecedentes patologicos, DRS-84) y HU-13 (condiciones hereditarias,
 * DRS-85): cada prueba corresponde a un criterio de aceptacion.
 */
class AntecedentesDelExpedienteIT extends PruebaClinica {

    private String medico;
    private long paciente;

    @BeforeEach
    void abrirUnExpediente() throws Exception {
        medico = tokenDeMedico();
        paciente = crearPaciente(medico);
    }

    private JsonNode leer(String ruta, String token) throws Exception {
        String cuerpo = mockMvc.perform(get(ruta).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private JsonNode registrarAntecedente(String tipo, String descripcion, String fecha, String estado)
            throws Exception {
        String cuerpo = mockMvc.perform(post("/antecedentes-patologicos")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"tipo":"%s","descripcion":"%s","fecha":"%s","estado":"%s"}
                                """.formatted(paciente, tipo, descripcion, fecha, estado)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private JsonNode registrarCondicion(String nombre, String parentesco) throws Exception {
        String cuerpo = mockMvc.perform(post("/condiciones-hereditarias")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"nombre":"%s","parentesco":"%s"}
                                """.formatted(paciente, nombre, parentesco)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    // ══════════════════════════════════════════════════════════════════════
    // HU-12 · Antecedentes patologicos
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("HU-12 c1: el antecedente se guarda con tipo, descripcion, fecha, estado y quien lo registro")
    void unAntecedenteQuedaGuardado() throws Exception {
        JsonNode creado = registrarAntecedente("CIRUGIA", "Apendicectomia", "2019-03-14", "RESUELTO");

        assertEquals("CIRUGIA", creado.get("tipo").asText());
        assertEquals("Apendicectomia", creado.get("descripcion").asText());
        assertEquals("2019-03-14", creado.get("fecha").asText());
        assertEquals("RESUELTO", creado.get("estado").asText());
        assertEquals(paciente, creado.get("pacienteId").asLong());
        assertFalse(creado.get("registradoPor").asText().isBlank());

        JsonNode lista = leer("/antecedentes-patologicos?pacienteId=" + paciente, medico);
        assertEquals(1, lista.size());
    }

    @Test
    @DisplayName("HU-12 c1: el tipo y el estado solo aceptan los valores de la lista")
    void elTipoYElEstadoSonListasCerradas() throws Exception {
        mockMvc.perform(post("/antecedentes-patologicos")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"tipo":"ALERGIA","descripcion":"x","fecha":"2020-01-01","estado":"ACTIVO"}
                                """.formatted(paciente)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/antecedentes-patologicos")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"tipo":"ENFERMEDAD","descripcion":"x","fecha":"2020-01-01","estado":"CURADO"}
                                """.formatted(paciente)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("HU-12 c1: la fecha no puede ser futura")
    void laFechaNoPuedeSerFutura() throws Exception {
        mockMvc.perform(post("/antecedentes-patologicos")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"tipo":"ENFERMEDAD","descripcion":"x","fecha":"%s","estado":"ACTIVO"}
                                """.formatted(paciente, LocalDate.now().plusDays(2))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("HU-12 c2: se listan del mas reciente al mas antiguo")
    void seListanDelMasRecienteAlMasAntiguo() throws Exception {
        registrarAntecedente("ENFERMEDAD", "Varicela", "2001-06-01", "RESUELTO");
        registrarAntecedente("HOSPITALIZACION", "Neumonia", "2022-11-20", "RESUELTO");
        registrarAntecedente("ENFERMEDAD", "Hipertension", "2015-02-10", "ACTIVO");

        JsonNode lista = leer("/antecedentes-patologicos?pacienteId=" + paciente, medico);

        assertEquals("2022-11-20", lista.get(0).get("fecha").asText());
        assertEquals("2015-02-10", lista.get(1).get("fecha").asText());
        assertEquals("2001-06-01", lista.get(2).get("fecha").asText());
    }

    @Test
    @DisplayName("HU-12 c3: la enfermera los consulta pero no los registra, edita ni elimina")
    void laEnfermeraSoloConsultaAntecedentes() throws Exception {
        long id = registrarAntecedente("CIRUGIA", "Colecistectomia", "2018-08-08", "RESUELTO")
                .get("antecedenteId").asLong();
        String enfermera = tokenDeEnfermera();

        assertEquals(1, leer("/antecedentes-patologicos?pacienteId=" + paciente, enfermera).size());

        mockMvc.perform(post("/antecedentes-patologicos")
                        .header("Authorization", bearer(enfermera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"tipo":"ENFERMEDAD","descripcion":"x","fecha":"2020-01-01","estado":"ACTIVO"}
                                """.formatted(paciente)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/antecedentes-patologicos/" + id)
                        .header("Authorization", bearer(enfermera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"CIRUGIA","descripcion":"cambiada","fecha":"2018-08-08","estado":"ACTIVO"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/antecedentes-patologicos/" + id)
                        .header("Authorization", bearer(enfermera)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/antecedentes-patologicos/" + id)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("HU-12 c4: un paciente sin antecedentes devuelve una lista vacia, no un error")
    void sinAntecedentesEsUnaListaVacia() throws Exception {
        assertEquals(0, leer("/antecedentes-patologicos?pacienteId=" + paciente, medico).size());
    }

    @Test
    @DisplayName("un paciente que no existe da 404")
    void unPacienteInexistenteDa404() throws Exception {
        mockMvc.perform(get("/antecedentes-patologicos?pacienteId=999999")
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════
    // HU-13 · Condiciones hereditarias
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("HU-13 c1: la condicion se guarda con el parentesco y queda visible")
    void unaCondicionQuedaGuardada() throws Exception {
        JsonNode creada = registrarCondicion("Diabetes mellitus", "MADRE");

        assertEquals("MADRE", creada.get("parentesco").asText());
        assertEquals(paciente, creada.get("pacienteId").asLong());

        JsonNode lista = leer("/condiciones-hereditarias?pacienteId=" + paciente, medico);
        assertEquals(1, lista.size());
        assertEquals("Diabetes mellitus", lista.get(0).get("nombre").asText());
    }

    @Test
    @DisplayName("HU-13 c2: el parentesco solo acepta valores de la lista controlada")
    void elParentescoEsUnaListaControlada() throws Exception {
        mockMvc.perform(post("/condiciones-hereditarias")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"nombre":"Asma","parentesco":"VECINO"}
                                """.formatted(paciente)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("HU-13 c3: salen ordenadas por parentesco, que es como se agrupan")
    void salenOrdenadasPorParentesco() throws Exception {
        registrarCondicion("Asma", "HERMANA");
        registrarCondicion("Hipertension", "PADRE");
        registrarCondicion("Cancer de colon", "ABUELO");
        registrarCondicion("Diabetes", "PADRE");

        JsonNode lista = leer("/condiciones-hereditarias?pacienteId=" + paciente, medico);

        assertEquals("PADRE", lista.get(0).get("parentesco").asText());
        assertEquals("Diabetes", lista.get(0).get("nombre").asText());
        assertEquals("PADRE", lista.get(1).get("parentesco").asText());
        assertEquals("ABUELO", lista.get(2).get("parentesco").asText());
        assertEquals("HERMANA", lista.get(3).get("parentesco").asText());
    }

    @Test
    @DisplayName("HU-13 c4: la enfermera la consulta pero no la modifica")
    void laEnfermeraSoloConsultaCondiciones() throws Exception {
        int id = registrarCondicion("Glaucoma", "ABUELA").get("condicionHereditariaId").asInt();
        String enfermera = tokenDeEnfermera();

        assertEquals(1, leer("/condiciones-hereditarias?pacienteId=" + paciente, enfermera).size());

        mockMvc.perform(post("/condiciones-hereditarias")
                        .header("Authorization", bearer(enfermera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"nombre":"Asma","parentesco":"PADRE"}
                                """.formatted(paciente)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/condiciones-hereditarias/" + id)
                        .header("Authorization", bearer(enfermera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentesco":"MADRE"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/condiciones-hereditarias/" + id)
                        .header("Authorization", bearer(enfermera)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un paciente sin condiciones hereditarias devuelve una lista vacia")
    void sinCondicionesEsUnaListaVacia() throws Exception {
        assertEquals(0, leer("/condiciones-hereditarias?pacienteId=" + paciente, medico).size());
    }
}
