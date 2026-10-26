package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-24 · Alerta de alergia al prescribir (DRS-93): una prueba por criterio de
 * aceptacion del backend.
 *
 * Lo que es de pantalla -que la alerta bloquee, que aparezca la casilla- se
 * prueba en el frontend; aqui se prueba lo que no puede dejarse al cliente: la
 * coincidencia, que el servidor exija la justificacion y la confirmacion aunque
 * la pantalla se salte, y que quede constancia.
 *
 * Cada prueba usa sustancias y medicamentos propios (con el contador de la
 * clase base en el nombre) porque la base no se limpia entre pruebas.
 */
class AlertaDeAlergiaAlPrescribirIT extends PruebaClinica {

    @Autowired private JdbcTemplate jdbc;

    private String medico;
    private long paciente;
    private long consultaId;
    private int n;

    @BeforeEach
    void prepararUnaConsulta() throws Exception {
        medico = tokenDeMedico();
        paciente = crearPaciente(medico);
        consultaId = crearConsultaSimple(medico, paciente).get("consultaId").asLong();
        n = siguiente();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 1: el sistema muestra sustancia, severidad y reaccion
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("c1: el endpoint devuelve sustancia, severidad y reaccion de la alergia que coincide, sin tildes ni mayusculas")
    void elEndpointDevuelveLaCoincidencia() throws Exception {
        long alergia = registrarAlergia("ÁCIDO ZETA " + n, "SEVERA", "Anafilaxia");
        long medicamento = crearMedicamento("Zetalgina " + n, "acido zeta " + n + " monohidratado");

        JsonNode alertas = alertas(medicamento);

        assertEquals(1, alertas.size());
        JsonNode alerta = alertas.get(0);
        assertEquals(alergia, alerta.get("alergiaId").asLong());
        assertEquals("ÁCIDO ZETA " + n, alerta.get("sustancia").asText());
        assertEquals("SEVERA", alerta.get("severidad").asText());
        assertEquals("Anafilaxia", alerta.get("reaccion").asText());
        assertEquals(medicamento, alerta.get("medicamentoId").asLong());
        assertTrue(alerta.get("medicamento").asText().contains("Zetalgina " + n));
    }

    @Test
    @DisplayName("c1: tambien coincide por el nombre generico, y una alergia eliminada ya no avisa")
    void coincidePorGenericoYIgnoraLasEliminadas() throws Exception {
        long alergia = registrarAlergia("Nomen" + n, "LEVE", "Picazon");
        long medicamento = crearMedicamento("Nomen" + n + " retard", "Principio sin relacion " + n);

        assertEquals(1, alertas(medicamento).size());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/alergias/{id}", alergia)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().is2xxSuccessful());

        assertEquals(0, alertas(medicamento).size(), "una alergia dada de baja no debe alertar");
    }

    @Test
    @DisplayName("c1: no se inventan familias farmaceuticas: solo coincide el texto contenido")
    void noHayCoincidenciaPorFamilia() throws Exception {
        registrarAlergia("Penicilina " + n, "SEVERA", "Anafilaxia");
        long medicamento = crearMedicamento("Amoxi " + n, "Amoxicilina " + n);

        assertEquals(0, alertas(medicamento).size());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 2: decidir recetarlo exige una justificacion que se guarda
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("c2: sin justificacion la receta se rechaza con 409; con ella se emite y queda guardada en la receta")
    void laJustificacionEsObligatoriaYQuedaGuardada() throws Exception {
        registrarAlergia("Moderol" + n, "MODERADA", "Erupcion");
        long medicamento = crearMedicamento("Moderol Forte " + n, "Moderol" + n + " base");

        String mensaje = intentarEmitir(medicamento, null, null, "No es una receta valida")
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(mensaje.contains("Moderol" + n), "el 409 debe nombrar la sustancia: " + mensaje);
        assertTrue(mensaje.contains("MODERADA"), "el 409 debe decir la severidad: " + mensaje);

        // Una justificacion en blanco tampoco cuenta.
        intentarEmitir(medicamento, "   ", null, "x").andExpect(status().isConflict());
        assertEquals(0, recetasDeLaConsulta(), "las rechazadas no dejan receta");

        JsonNode receta = json.readTree(
                intentarEmitir(medicamento, "  Beneficio mayor que el riesgo  ", null, "x")
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString());
        assertEquals("Beneficio mayor que el riesgo", receta.get("justificacionAlergia").asText());

        // Persistida: se vuelve a leer por GET, no solo en la respuesta del POST.
        JsonNode leida = json.readTree(mockMvc.perform(get("/prescripciones/{id}", receta.get("prescripcionId").asLong())
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals("Beneficio mayor que el riesgo", leida.get("justificacionAlergia").asText());
    }

    @Test
    @DisplayName("c2: una justificacion de mas de 500 caracteres es 400")
    void laJustificacionTieneTope() throws Exception {
        long medicamento = crearMedicamento("Tope " + n, "Tope" + n);

        intentarEmitir(medicamento, "x".repeat(501), null, "x").andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 3: severa pide una segunda confirmacion explicita
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("c3: con alergia SEVERA la justificacion sola no basta: hace falta confirmaAlergiaSevera=true")
    void laAlergiaSeveraPideSegundaConfirmacion() throws Exception {
        registrarAlergia("Severol" + n, "SEVERA", "Anafilaxia");
        long medicamento = crearMedicamento("Severol " + n, "Severol" + n);

        String mensaje = intentarEmitir(medicamento, "Es el unico disponible", null, "x")
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(mensaje.contains("SEVERA"), mensaje);

        intentarEmitir(medicamento, "Es el unico disponible", false, "x").andExpect(status().isConflict());
        assertEquals(0, recetasDeLaConsulta());

        intentarEmitir(medicamento, "Es el unico disponible", true, "x").andExpect(status().isCreated());
        assertEquals(1, recetasDeLaConsulta());
    }

    @Test
    @DisplayName("c3: con alergia que no es severa no se pide la segunda confirmacion")
    void laNoSeveraNoPideSegundaConfirmacion() throws Exception {
        registrarAlergia("Levecina" + n, "LEVE", "Picazon");
        long medicamento = crearMedicamento("Levecina " + n, "Levecina" + n);

        intentarEmitir(medicamento, "Lo tolera bien", null, "x").andExpect(status().isCreated());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 4: sin coincidencias, el flujo no cambia
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("c4: sin alergias coincidentes no hay alertas y la receta se emite sin justificacion")
    void sinCoincidenciasElFlujoContinua() throws Exception {
        registrarAlergia("Otra cosa " + n, "SEVERA", "Anafilaxia");
        long medicamento = crearMedicamento("Inocuo " + n, "Inocuo" + n);

        assertEquals(0, alertas(medicamento).size());

        JsonNode receta = json.readTree(intentarEmitir(medicamento, null, null, "1 tableta")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertTrue(receta.get("justificacionAlergia").isNull());

        assertEquals(0, eventosDeAlergiaDe(receta.get("prescripcionId").asLong()).size(),
                "sin alerta no hay nada que registrar");
    }

    @Test
    @DisplayName("c4: una justificacion que sobra no se guarda si no hubo alerta")
    void laJustificacionQueSobraSeDescarta() throws Exception {
        long medicamento = crearMedicamento("Sobra " + n, "Sobra" + n);

        JsonNode receta = json.readTree(intentarEmitir(medicamento, "por si acaso", true, "x")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertNull(jdbc.queryForObject("SELECT justificacion_alergia FROM prescripciones WHERE prescripcion_id = ?",
                String.class, receta.get("prescripcionId").asLong()));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 5: la alerta y la decision quedan en la bitacora
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("c5: la receta emitida pese a la alergia deja un evento PROCESSED con sustancia, severidad, medicamento y justificacion")
    void laDecisionQuedaEnLaBitacora() throws Exception {
        registrarAlergia("Bitacorina" + n, "SEVERA", "Anafilaxia");
        long medicamento = crearMedicamento("Bitacorina " + n, "Bitacorina" + n);

        long recetaId = json.readTree(intentarEmitir(medicamento, "Sin alternativa disponible", true, "x")
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("prescripcionId").asLong();

        List<Map<String, Object>> eventos = eventosDeAlergiaDe(recetaId);
        assertEquals(1, eventos.size());
        Map<String, Object> evento = eventos.get(0);

        assertEquals(3, ((Number) evento.get("event_status_id")).intValue(), "debe quedar PROCESSED para que nadie lo tome");
        String descripcion = (String) evento.get("description");
        assertTrue(descripcion.contains("Bitacorina" + n), descripcion);
        assertTrue(descripcion.contains("SEVERA"), descripcion);
        assertTrue(descripcion.contains("Bitacorina " + n), descripcion);
        assertTrue(descripcion.contains("Sin alternativa disponible"), descripcion);

        String correo = (String) evento.get("user_email");
        assertFalse(correo == null || correo.isBlank(), "falta el medico que decidio");
        Integer usuarios = jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, correo);
        assertEquals(1, usuarios, "user_email debe ser el correo de sesion del medico: " + correo);
    }

    @Test
    @DisplayName("c5: una receta rechazada por falta de justificacion no deja evento (nada se decidio)")
    void laRechazadaNoDejaEvento() throws Exception {
        registrarAlergia("Rechazol" + n, "LEVE", "Picazon");
        long medicamento = crearMedicamento("Rechazol " + n, "Rechazol" + n);

        intentarEmitir(medicamento, null, null, "x").andExpect(status().isConflict());

        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM events WHERE description LIKE ?", Integer.class, "%Rechazol" + n + "%");
        assertEquals(0, total);
    }

    @Test
    @DisplayName("permisos: la enfermera no consulta la alerta (solo medico y admin)")
    void laEnfermeraNoConsultaAlertas() throws Exception {
        long medicamento = crearMedicamento("Permiso " + n, "Permiso" + n);

        mockMvc.perform(get("/prescripciones/alertas-de-alergia")
                        .param("pacienteId", String.valueOf(paciente))
                        .param("medicamentoId", String.valueOf(medicamento))
                        .header("Authorization", bearer(tokenDeEnfermera())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/prescripciones/alertas-de-alergia")
                        .param("pacienteId", "999999999")
                        .param("medicamentoId", String.valueOf(medicamento))
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    private long registrarAlergia(String sustancia, String severidad, String reaccion) throws Exception {
        String cuerpo = mockMvc.perform(post("/alergias")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"sustancia":"%s","reaccion":"%s",
                                 "severidad":"%s","fechaDeteccion":"2021-07-02"}
                                """.formatted(paciente, sustancia, reaccion, severidad)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("alergiaId").asLong();
    }

    private long crearMedicamento(String generico, String principioActivo) throws Exception {
        var nodo = json.createObjectNode();
        nodo.put("nombreGenerico", generico);
        nodo.put("nombreComercial", "Comercial " + generico);
        nodo.put("principioActivo", principioActivo);
        nodo.put("presentacion", "Tableta");
        nodo.put("concentracion", "10 mg");
        String cuerpo = mockMvc.perform(post("/medicamentos")
                        .header("Authorization", bearer(tokenDeAdministrador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(nodo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("medicamentoId").asLong();
    }

    private JsonNode alertas(long medicamentoId) throws Exception {
        return json.readTree(mockMvc.perform(get("/prescripciones/alertas-de-alergia")
                        .param("pacienteId", String.valueOf(paciente))
                        .param("medicamentoId", String.valueOf(medicamentoId))
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions intentarEmitir(long medicamentoId, String justificacion, Boolean confirma, String dosis)
            throws Exception {
        var cuerpo = json.createObjectNode();
        cuerpo.put("consultaId", consultaId);
        var renglon = cuerpo.putArray("medicamentos").addObject();
        renglon.put("medicamentoId", medicamentoId);
        renglon.put("dosis", dosis);
        if (justificacion != null) cuerpo.put("justificacionAlergia", justificacion);
        if (confirma != null) cuerpo.put("confirmaAlergiaSevera", confirma);

        return mockMvc.perform(post("/prescripciones")
                .header("Authorization", bearer(medico))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)));
    }

    private int recetasDeLaConsulta() {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM prescripciones WHERE consulta_id = ?", Integer.class, consultaId);
        return total == null ? 0 : total;
    }

    private List<Map<String, Object>> eventosDeAlergiaDe(long prescripcionId) {
        return jdbc.queryForList(
                "SELECT event_status_id, description, user_email FROM events "
                        + "WHERE event_type_id = 10 AND ref = ?",
                String.valueOf(prescripcionId));
    }
}
