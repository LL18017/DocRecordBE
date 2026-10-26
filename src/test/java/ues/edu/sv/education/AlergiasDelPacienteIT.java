package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-11 · Registro de alergias del paciente (DRS-83): una prueba por criterio
 * de aceptacion, mas los permisos y los 404.
 *
 * Quien registra se comprueba por el nombre: tokenDeMedico() crea a "Medico
 * Del Turno" y tokenDeEnfermera() a "Enfermera De Turno" (ver PruebaClinica).
 */
class AlergiasDelPacienteIT extends PruebaClinica {

    private String medico;
    private long paciente;

    @BeforeEach
    void abrirUnExpediente() throws Exception {
        medico = tokenDeMedico();
        paciente = crearPaciente(medico);
    }

    private ResultActions intentarRegistrar(String token, String sustancia, String severidad, String fecha)
            throws Exception {
        return mockMvc.perform(post("/alergias")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pacienteId":%d,"sustancia":"%s","reaccion":"Urticaria",
                         "severidad":"%s","fechaDeteccion":"%s"}
                        """.formatted(paciente, sustancia, severidad, fecha)));
    }

    private JsonNode registrar(String token, String sustancia, String severidad) throws Exception {
        String cuerpo = intentarRegistrar(token, sustancia, severidad, "2021-07-02")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private JsonNode leer(String ruta, String token) throws Exception {
        String cuerpo = mockMvc.perform(get(ruta).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private JsonNode listar() throws Exception {
        return leer("/alergias?pacienteId=" + paciente, medico);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 1: queda guardada y visible en su seccion
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("c1: la alergia se guarda con sustancia, reaccion, severidad y fecha, y se lista")
    void unaAlergiaQuedaGuardadaYVisible() throws Exception {
        JsonNode creada = registrar(medico, "Penicilina", "SEVERA");

        assertEquals(paciente, creada.get("pacienteId").asLong());
        assertEquals("Penicilina", creada.get("sustancia").asText());
        assertEquals("Urticaria", creada.get("reaccion").asText());
        assertEquals("SEVERA", creada.get("severidad").asText());
        assertEquals("2021-07-02", creada.get("fechaDeteccion").asText());

        JsonNode lista = listar();
        assertEquals(1, lista.size());
        assertEquals(creada.get("alergiaId").asInt(), lista.get(0).get("alergiaId").asInt());
    }

    @Test
    @DisplayName("c1: la enfermera tambien registra, y el medico la ve")
    void laEnfermeraRegistra() throws Exception {
        registrar(tokenDeEnfermera(), "Mariscos", "MODERADA");

        assertEquals("Mariscos", listar().get(0).get("sustancia").asText());
    }

    @Test
    @DisplayName("c1: la severidad es de lista cerrada, la fecha no es futura y nada es opcional")
    void losDatosSeValidan() throws Exception {
        intentarRegistrar(medico, "Ibuprofeno", "ALTA", "2021-07-02")
                .andExpect(status().isBadRequest());

        intentarRegistrar(medico, "Ibuprofeno", "LEVE", LocalDate.now().plusDays(1).toString())
                .andExpect(status().isBadRequest());

        intentarRegistrar(medico, " ", "LEVE", "2021-07-02")
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/alergias")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"sustancia":"Ibuprofeno","severidad":"LEVE",
                                 "fechaDeteccion":"2021-07-02"}
                                """.formatted(paciente)))
                .andExpect(status().isBadRequest());

        assertEquals(0, listar().size());
    }

    @Test
    @DisplayName("c1: un paciente sin alergias devuelve una lista vacia, no un error")
    void sinAlergiasEsUnaListaVacia() throws Exception {
        assertEquals(0, listar().size());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 2: las severas primero (la ficha las destaca arriba)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("c2: el listado pone las SEVERAS primero, luego MODERADAS y LEVES")
    void lasSeverasVanPrimero() throws Exception {
        registrar(medico, "Polen", "LEVE");
        registrar(medico, "Sulfas", "SEVERA");
        registrar(medico, "Latex", "MODERADA");
        registrar(medico, "Aspirina", "SEVERA");

        JsonNode lista = listar();

        assertEquals("Aspirina", lista.get(0).get("sustancia").asText());
        assertEquals("SEVERA", lista.get(0).get("severidad").asText());
        assertEquals("Sulfas", lista.get(1).get("sustancia").asText());
        assertEquals("MODERADA", lista.get(2).get("severidad").asText());
        assertEquals("LEVE", lista.get(3).get("severidad").asText());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 3: la misma sustancia no se registra dos veces
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("c3: repetir la sustancia, aunque cambien mayusculas o tildes, da 409 y nombra la existente")
    void unaSustanciaRepetidaSeRechaza() throws Exception {
        registrar(medico, "Penicilina", "SEVERA");

        String cuerpo = intentarRegistrar(tokenDeEnfermera(), "  PENICILÍNA ", "LEVE", "2023-01-01")
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        String mensaje = json.readTree(cuerpo).get("message").asText();
        assertTrue(mensaje.contains("Penicilina"), mensaje);
        assertTrue(mensaje.contains("SEVERA"), mensaje);
        assertTrue(mensaje.contains("2021-07-02"), mensaje);

        assertEquals(1, listar().size());
    }

    @Test
    @DisplayName("c3: la misma sustancia en otro paciente no es un duplicado")
    void otroPacientePuedeTenerLaMismaSustancia() throws Exception {
        registrar(medico, "Penicilina", "SEVERA");
        long otro = crearPaciente(medico);

        mockMvc.perform(post("/alergias")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"sustancia":"Penicilina","reaccion":"Edema",
                                 "severidad":"LEVE","fechaDeteccion":"2021-07-02"}
                                """.formatted(otro)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("c3: una alergia eliminada no impide volver a registrar la sustancia")
    void unaEliminadaNoCuentaComoDuplicado() throws Exception {
        int id = registrar(medico, "Penicilina", "LEVE").get("alergiaId").asInt();

        mockMvc.perform(delete("/alergias/" + id).header("Authorization", bearer(medico)))
                .andExpect(status().isNoContent());

        JsonNode nueva = registrar(medico, "Penicilina", "SEVERA");
        assertEquals("SEVERA", nueva.get("severidad").asText());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 4: queda quien la agrego o la elimino y cuando
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("c4: al registrar queda quien lo hizo y cuando, tomado del token y no del cuerpo")
    void alRegistrarQuedaElAutor() throws Exception {
        String enfermera = tokenDeEnfermera();

        // Un registradaPor en el cuerpo se ignora: el autor sale del token.
        String cuerpo = mockMvc.perform(post("/alergias")
                        .header("Authorization", bearer(enfermera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"sustancia":"Yodo","reaccion":"Prurito","severidad":"MODERADA",
                                 "fechaDeteccion":"2022-02-02","registradaPor":"Otra Persona"}
                                """.formatted(paciente)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode creada = json.readTree(cuerpo);

        assertEquals("Enfermera De Turno", creada.get("registradaPor").asText());
        assertEquals(LocalDate.now().toString(), creada.get("registradaEn").asText().substring(0, 10));
        assertTrue(creada.get("eliminadaPor").isNull());

        assertEquals("Enfermera De Turno", listar().get(0).get("registradaPor").asText());
    }

    @Test
    @DisplayName("c4: eliminar es baja logica: deja de listarse pero queda quien y cuando")
    void alEliminarQuedaQuienYCuando() throws Exception {
        int id = registrar(medico, "Penicilina", "SEVERA").get("alergiaId").asInt();

        mockMvc.perform(delete("/alergias/" + id).header("Authorization", bearer(tokenDeEnfermera())))
                .andExpect(status().isNoContent());

        assertEquals(0, listar().size());

        JsonNode eliminada = leer("/alergias/" + id, medico);
        assertEquals("Penicilina", eliminada.get("sustancia").asText());
        assertEquals("Medico Del Turno", eliminada.get("registradaPor").asText());
        assertEquals("Enfermera De Turno", eliminada.get("eliminadaPor").asText());
        assertFalse(eliminada.get("eliminadaEn").isNull());
        assertEquals(LocalDate.now().toString(), eliminada.get("eliminadaEn").asText().substring(0, 10));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Permisos
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("el admin consulta las alergias pero no las registra ni las elimina")
    void elAdminSoloConsulta() throws Exception {
        int id = registrar(medico, "Penicilina", "SEVERA").get("alergiaId").asInt();
        String admin = tokenDeAdministrador();

        assertEquals(1, leer("/alergias?pacienteId=" + paciente, admin).size());

        intentarRegistrar(admin, "Latex", "LEVE", "2021-07-02")
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/alergias/" + id).header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden());

        assertEquals(1, listar().size());
    }

    // ══════════════════════════════════════════════════════════════════════
    // 404
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("un paciente que no existe da 404, al listar y al registrar")
    void unPacienteInexistenteDa404() throws Exception {
        mockMvc.perform(get("/alergias?pacienteId=999999").header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/alergias")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":999999,"sustancia":"Latex","reaccion":"Eritema",
                                 "severidad":"LEVE","fechaDeteccion":"2021-07-02"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("eliminar una alergia que no existe, o que ya se elimino, da 404")
    void eliminarLoQueNoEstaDa404() throws Exception {
        mockMvc.perform(delete("/alergias/999999").header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());

        int id = registrar(medico, "Penicilina", "SEVERA").get("alergiaId").asInt();
        mockMvc.perform(delete("/alergias/" + id).header("Authorization", bearer(medico)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/alergias/" + id).header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());
    }
}
