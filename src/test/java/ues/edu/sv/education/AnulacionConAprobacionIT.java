package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Anulacion de consultas (HU-21) y recetas (HU-22 c3) con ventana y
 * aprobacion de administradores (V35, ver AnulacionService).
 *
 * Para salir de la ventana de anulacion directa no se cambian los parametros:
 * se le corre hacia atras la hora de registro al documento, directo en la
 * base. Cambiar los parametros afectaria a cualquier otra prueba que anule;
 * las pocas que si los cambian los dejan como estaban en @AfterEach.
 */
class AnulacionConAprobacionIT extends PruebaClinica {

    @Autowired private JdbcTemplate jdbc;

    private static final String MOTIVO = "{\"motivo\":\"Consulta registrada al paciente equivocado\"}";

    private String medico;
    private long paciente;
    private long amoxicilina;

    @BeforeEach
    void preparar() throws Exception {
        medico = tokenDeMedico();
        paciente = crearPaciente(medico);
        amoxicilina = jdbc.queryForObject(
                "SELECT medicamento_id FROM medicamentos WHERE nombre_comercial = 'Amoxil' AND presentacion = 'Cápsula'",
                Long.class);
    }

    @AfterEach
    void restaurarParametros() {
        jdbc.update("UPDATE parametros_de_anulacion SET minutos_anulacion_directa = 10, "
                + "aprobaciones_requeridas = 2, dias_maximos = NULL");
    }

    // ══════════════════════════════════════════════════════════════════════
    // HU-21 · dentro de la ventana, anulacion directa
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("HU-21 c2 y c5: dentro de la ventana la consulta queda ANULADA con motivo, autor y fecha, y sigue visible")
    void dentroDeLaVentanaSeAnulaDirecto() throws Exception {
        long consultaId = nuevaConsulta();

        JsonNode anulada = cuerpo(anularConsulta(medico, consultaId, MOTIVO).andExpect(status().isOk()));
        assertEquals("ANULADA", anulada.get("estado").asText());
        assertEquals("Consulta registrada al paciente equivocado", anulada.get("motivoAnulacion").asText());
        assertTrue(anulada.hasNonNull("anuladaEn"));
        assertTrue(anulada.get("anuladaPor").asText().contains("Medico"), "falta quien la anulo");

        // Sigue en el historial del paciente, marcada.
        JsonNode historial = cuerpo(mockMvc.perform(get("/consultas").param("pacienteId", String.valueOf(paciente))
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk()));
        assertEquals(1, historial.size());
        assertEquals("ANULADA", historial.get(0).get("estado").asText());
    }

    @Test
    @DisplayName("HU-21 c2: una consulta anulada no se edita ni recibe recetas, y no se anula dos veces")
    void unaConsultaAnuladaNoSeToca() throws Exception {
        long consultaId = nuevaConsulta();
        anularConsulta(medico, consultaId, MOTIVO).andExpect(status().isOk());

        mockMvc.perform(put("/consultas/{id}", consultaId)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Otro motivo\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receta(consultaId)))
                .andExpect(status().isConflict());

        anularConsulta(medico, consultaId, "{\"motivo\":\"Segundo intento de anular\"}")
                .andExpect(status().isConflict());
        assertEquals("Consulta registrada al paciente equivocado",
                cuerpo(leerConsulta(consultaId)).get("motivoAnulacion").asText());
    }

    @Test
    @DisplayName("HU-21 c4: anular la consulta anula sus recetas vigentes")
    void anularLaConsultaAnulaSusRecetas() throws Exception {
        long consultaId = nuevaConsulta();
        long recetaId = emitir(consultaId);

        anularConsulta(medico, consultaId, MOTIVO).andExpect(status().isOk());

        JsonNode laReceta = cuerpo(mockMvc.perform(get("/prescripciones/{id}", recetaId)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk()));
        assertEquals("ANULADA", laReceta.get("estado").asText());
        assertTrue(laReceta.get("motivoAnulacion").asText().contains("paciente equivocado"));
    }

    @Test
    @DisplayName("HU-21 c1: el motivo tiene que tener de 10 a 500 caracteres")
    void elMotivoTieneLimites() throws Exception {
        long consultaId = nuevaConsulta();

        anularConsulta(medico, consultaId, "{\"motivo\":\"corto\"}").andExpect(status().isBadRequest());
        anularConsulta(medico, consultaId, "{\"motivo\":\"%s\"}".formatted("x".repeat(501)))
                .andExpect(status().isBadRequest());
        anularConsulta(medico, consultaId, "{}").andExpect(status().isBadRequest());

        assertEquals("FINALIZADA", cuerpo(leerConsulta(consultaId)).get("estado").asText());
    }

    @Test
    @DisplayName("HU-21 c3: otro medico y enfermeria reciben 403; un administrador si puede")
    void quienPuedeAnularUnaConsulta() throws Exception {
        long consultaId = nuevaConsulta();

        anularConsulta(tokenDeMedico(), consultaId, MOTIVO).andExpect(status().isForbidden());
        anularConsulta(tokenDeEnfermera(), consultaId, MOTIVO).andExpect(status().isForbidden());
        assertEquals("FINALIZADA", cuerpo(leerConsulta(consultaId)).get("estado").asText());

        anularConsulta(tokenDeAdministrador(), consultaId, MOTIVO).andExpect(status().isOk());

        anularConsulta(medico, 9_999_999L, MOTIVO).andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Fuera de la ventana: solicitud y aprobaciones
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("HU-21 c6: fuera de la ventana queda una solicitud; la consulta sigue vigente y no se pide dos veces")
    void fueraDeLaVentanaQuedaUnaSolicitud() throws Exception {
        long consultaId = nuevaConsultaDeHaceUnaHora();

        JsonNode solicitud = cuerpo(anularConsulta(medico, consultaId, MOTIVO).andExpect(status().isAccepted()));
        assertEquals("PENDIENTE", solicitud.get("estado").asText());
        assertEquals("CONSULTA", solicitud.get("tipo").asText());
        assertEquals(2, solicitud.get("aprobacionesRequeridas").asInt());
        assertEquals(0, solicitud.get("aprobaciones").size());
        assertFalse(solicitud.get("puedeResolver").asBoolean(), "quien la pide no la resuelve");

        assertEquals("FINALIZADA", cuerpo(leerConsulta(consultaId)).get("estado").asText());

        anularConsulta(medico, consultaId, MOTIVO).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("HU-21 c6: hacen falta dos administradores distintos; con la segunda aprobacion la consulta se anula")
    void dosAprobacionesAnulanLaConsulta() throws Exception {
        long consultaId = nuevaConsultaDeHaceUnaHora();
        long recetaId = emitir(consultaId);
        long solicitudId = cuerpo(anularConsulta(medico, consultaId, MOTIVO).andExpect(status().isAccepted()))
                .get("solicitudId").asLong();

        // El medico que la pidio no aprueba: no es administrador.
        aprobar(medico, solicitudId).andExpect(status().isForbidden());

        String primerAdmin = tokenDeAdministrador();
        JsonNode trasLaPrimera = cuerpo(aprobar(primerAdmin, solicitudId).andExpect(status().isOk()));
        assertEquals("PENDIENTE", trasLaPrimera.get("estado").asText());
        assertEquals(1, trasLaPrimera.get("aprobaciones").size());
        assertEquals("FINALIZADA", cuerpo(leerConsulta(consultaId)).get("estado").asText());

        // El mismo administrador no cuenta dos veces.
        aprobar(primerAdmin, solicitudId).andExpect(status().isConflict());

        JsonNode aprobada = cuerpo(aprobar(tokenDeAdministrador(), solicitudId).andExpect(status().isOk()));
        assertEquals("APROBADA", aprobada.get("estado").asText());
        assertEquals(2, aprobada.get("aprobaciones").size());

        JsonNode consulta = cuerpo(leerConsulta(consultaId));
        assertEquals("ANULADA", consulta.get("estado").asText());
        assertTrue(consulta.get("anuladaPor").asText().contains("Medico"),
                "el autor de la anulacion es quien la pidio, no quien la aprobo");
        assertEquals("ANULADA", cuerpo(mockMvc.perform(get("/prescripciones/{id}", recetaId)
                .header("Authorization", bearer(medico)))).get("estado").asText());

        // Resuelta, ya no se aprueba ni se rechaza.
        aprobar(tokenDeAdministrador(), solicitudId).andExpect(status().isConflict());
        rechazar(tokenDeAdministrador(), solicitudId).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("HU-21 c6: un administrador no aprueba la anulacion que pidio el mismo")
    void quienPideNoAprueba() throws Exception {
        long consultaId = nuevaConsultaDeHaceUnaHora();
        String admin = tokenDeAdministrador();
        long solicitudId = cuerpo(anularConsulta(admin, consultaId, MOTIVO).andExpect(status().isAccepted()))
                .get("solicitudId").asLong();

        aprobar(admin, solicitudId).andExpect(status().isForbidden());
        rechazar(admin, solicitudId).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HU-21 c6: un rechazo basta: la consulta sigue vigente y la anulacion se puede volver a pedir")
    void unRechazoDejaLaConsultaVigente() throws Exception {
        long consultaId = nuevaConsultaDeHaceUnaHora();
        long solicitudId = cuerpo(anularConsulta(medico, consultaId, MOTIVO).andExpect(status().isAccepted()))
                .get("solicitudId").asLong();

        mockMvc.perform(post("/anulaciones/{id}/rechazar", solicitudId)
                        .header("Authorization", bearer(tokenDeAdministrador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"corto\"}"))
                .andExpect(status().isBadRequest());

        JsonNode rechazada = cuerpo(rechazar(tokenDeAdministrador(), solicitudId).andExpect(status().isOk()));
        assertEquals("RECHAZADA", rechazada.get("estado").asText());
        assertEquals("La consulta es correcta, no procede", rechazada.get("motivoRechazo").asText());
        assertEquals("FINALIZADA", cuerpo(leerConsulta(consultaId)).get("estado").asText());

        anularConsulta(medico, consultaId, MOTIVO).andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("HU-22 c3: la receta fuera de la ventana tambien pasa por aprobacion")
    void laRecetaFueraDeLaVentanaPideAprobacion() throws Exception {
        long consultaId = nuevaConsulta();
        long recetaId = emitir(consultaId);
        jdbc.update("UPDATE prescripciones SET fecha = now() - interval '1 hour' WHERE prescripcion_id = ?", recetaId);

        JsonNode solicitud = cuerpo(anularReceta(medico, recetaId).andExpect(status().isAccepted()));
        assertEquals("RECETA", solicitud.get("tipo").asText());
        assertEquals(recetaId, solicitud.get("prescripcionId").asLong());
        assertEquals(consultaId, solicitud.get("consultaId").asLong());

        long solicitudId = solicitud.get("solicitudId").asLong();
        aprobar(tokenDeAdministrador(), solicitudId).andExpect(status().isOk());
        aprobar(tokenDeAdministrador(), solicitudId).andExpect(status().isOk());

        assertEquals("ANULADA", cuerpo(mockMvc.perform(get("/prescripciones/{id}", recetaId)
                .header("Authorization", bearer(medico)))).get("estado").asText());
        // La consulta no se toca: se anulo la receta, no la atencion.
        assertEquals("FINALIZADA", cuerpo(leerConsulta(consultaId)).get("estado").asText());
    }

    @Test
    @DisplayName("HU-21 c4: si la consulta se anula, la solicitud en curso de su receta se cancela")
    void anularLaConsultaCancelaLasSolicitudesDeSusRecetas() throws Exception {
        long consultaId = nuevaConsulta();
        long recetaId = emitir(consultaId);
        jdbc.update("UPDATE prescripciones SET fecha = now() - interval '1 hour' WHERE prescripcion_id = ?", recetaId);
        long solicitudId = cuerpo(anularReceta(medico, recetaId).andExpect(status().isAccepted()))
                .get("solicitudId").asLong();

        anularConsulta(medico, consultaId, MOTIVO).andExpect(status().isOk());

        JsonNode solicitud = cuerpo(mockMvc.perform(get("/anulaciones/{id}", solicitudId)
                .header("Authorization", bearer(medico))).andExpect(status().isOk()));
        assertEquals("CANCELADA", solicitud.get("estado").asText());
        aprobar(tokenDeAdministrador(), solicitudId).andExpect(status().isConflict());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Parametros
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("HU-21 c7: los parametros los lee el medico y solo los cambia un administrador, dentro de sus limites")
    void losParametrosSonDelAdministrador() throws Exception {
        JsonNode parametros = cuerpo(mockMvc.perform(get("/anulaciones/parametros")
                .header("Authorization", bearer(medico))).andExpect(status().isOk()));
        assertEquals(2, parametros.size());
        assertEquals(10, parametros.get(0).get("minutosAnulacionDirecta").asInt());

        String nuevos = "{\"minutosAnulacionDirecta\":30,\"aprobacionesRequeridas\":1,\"diasMaximos\":15}";
        cambiarParametros(medico, "CONSULTA", nuevos).andExpect(status().isForbidden());
        cambiarParametros(tokenDeEnfermera(), "CONSULTA", nuevos).andExpect(status().isForbidden());

        String admin = tokenDeAdministrador();
        cambiarParametros(admin, "CONSULTA", "{\"minutosAnulacionDirecta\":-1,\"aprobacionesRequeridas\":1}")
                .andExpect(status().isBadRequest());
        cambiarParametros(admin, "CONSULTA", "{\"minutosAnulacionDirecta\":10,\"aprobacionesRequeridas\":6}")
                .andExpect(status().isBadRequest());
        cambiarParametros(admin, "CONSULTA", "{\"minutosAnulacionDirecta\":10,\"aprobacionesRequeridas\":0}")
                .andExpect(status().isBadRequest());

        JsonNode cambiado = cuerpo(cambiarParametros(admin, "CONSULTA", nuevos).andExpect(status().isOk()));
        assertEquals(30, cambiado.get("minutosAnulacionDirecta").asInt());
        assertEquals(1, cambiado.get("aprobacionesRequeridas").asInt());
        assertEquals(15, cambiado.get("diasMaximos").asInt());
        assertTrue(cambiado.get("actualizadoPor").asText().contains("Admin"));
    }

    @Test
    @DisplayName("HU-21 c5 y c7: con 0 minutos toda anulacion pide aprobacion, aunque la consulta sea de hace un momento")
    void ceroMinutosApagaLaAnulacionDirecta() throws Exception {
        cambiarParametros(tokenDeAdministrador(), "CONSULTA",
                "{\"minutosAnulacionDirecta\":0,\"aprobacionesRequeridas\":1}").andExpect(status().isOk());

        long consultaId = nuevaConsulta();
        long solicitudId = cuerpo(anularConsulta(medico, consultaId, MOTIVO).andExpect(status().isAccepted()))
                .get("solicitudId").asLong();

        // Con una sola aprobacion requerida, la primera la resuelve.
        assertEquals("APROBADA", cuerpo(aprobar(tokenDeAdministrador(), solicitudId)).get("estado").asText());
        assertEquals("ANULADA", cuerpo(leerConsulta(consultaId)).get("estado").asText());
    }

    @Test
    @DisplayName("HU-21 c7: pasado el tope de dias ya no se anula")
    void pasadoElTopeYaNoSeAnula() throws Exception {
        cambiarParametros(tokenDeAdministrador(), "CONSULTA",
                "{\"minutosAnulacionDirecta\":10,\"aprobacionesRequeridas\":2,\"diasMaximos\":1}")
                .andExpect(status().isOk());

        long consultaId = nuevaConsulta();
        jdbc.update("UPDATE consultas SET registrada_en = now() - interval '2 days' WHERE consulta_id = ?", consultaId);

        anularConsulta(medico, consultaId, MOTIVO).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("HU-21 c7: cambiar los parametros no mueve la meta de una solicitud que ya estaba en curso")
    void laSolicitudConservaSusAprobacionesRequeridas() throws Exception {
        long consultaId = nuevaConsultaDeHaceUnaHora();
        long solicitudId = cuerpo(anularConsulta(medico, consultaId, MOTIVO).andExpect(status().isAccepted()))
                .get("solicitudId").asLong();

        cambiarParametros(tokenDeAdministrador(), "CONSULTA",
                "{\"minutosAnulacionDirecta\":10,\"aprobacionesRequeridas\":1}").andExpect(status().isOk());

        assertEquals("PENDIENTE", cuerpo(aprobar(tokenDeAdministrador(), solicitudId)).get("estado").asText());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Quien ve las solicitudes
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("HU-21 c6: el medico ve sus solicitudes y las de una consulta; enfermeria no entra")
    void quienVeLasSolicitudes() throws Exception {
        long consultaId = nuevaConsultaDeHaceUnaHora();
        long solicitudId = cuerpo(anularConsulta(medico, consultaId, MOTIVO).andExpect(status().isAccepted()))
                .get("solicitudId").asLong();

        JsonNode mias = cuerpo(mockMvc.perform(get("/anulaciones").header("Authorization", bearer(medico)))
                .andExpect(status().isOk()));
        assertEquals(1, mias.size());
        assertEquals(solicitudId, mias.get(0).get("solicitudId").asLong());

        // Otro medico no la ve en "las suyas", pero si en las de la consulta.
        String otro = tokenDeMedico();
        assertEquals(0, cuerpo(mockMvc.perform(get("/anulaciones").header("Authorization", bearer(otro)))).size());
        assertEquals(1, cuerpo(mockMvc.perform(get("/anulaciones")
                .param("consultaId", String.valueOf(consultaId))
                .header("Authorization", bearer(otro)))).size());
        mockMvc.perform(get("/anulaciones/{id}", solicitudId).header("Authorization", bearer(otro)))
                .andExpect(status().isForbidden());

        // El administrador las ve todas, y puede resolver esta.
        JsonNode pendientes = cuerpo(mockMvc.perform(get("/anulaciones").param("estado", "PENDIENTE")
                .header("Authorization", bearer(tokenDeAdministrador()))).andExpect(status().isOk()));
        JsonNode esta = null;
        for (JsonNode s : pendientes) if (s.get("solicitudId").asLong() == solicitudId) esta = s;
        assertTrue(esta != null && esta.get("puedeResolver").asBoolean());

        mockMvc.perform(get("/anulaciones").header("Authorization", bearer(tokenDeEnfermera())))
                .andExpect(status().isForbidden());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    private long nuevaConsulta() throws Exception {
        return crearConsultaSimple(medico, paciente).get("consultaId").asLong();
    }

    /** Una consulta ya fuera de la ventana de 10 minutos. */
    private long nuevaConsultaDeHaceUnaHora() throws Exception {
        long consultaId = nuevaConsulta();
        jdbc.update("UPDATE consultas SET registrada_en = now() - interval '1 hour' WHERE consulta_id = ?", consultaId);
        return consultaId;
    }

    private String receta(long consultaId) {
        return """
                {"consultaId":%d,"medicamentos":[{"medicamentoId":%d,"dosis":"1 cápsula","viaAdministracion":"Oral",
                 "frecuencia":"Cada 8 horas","duracion":"7 dias"}]}
                """.formatted(consultaId, amoxicilina);
    }

    private long emitir(long consultaId) throws Exception {
        return cuerpo(mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receta(consultaId)))
                .andExpect(status().isCreated())).get("prescripcionId").asLong();
    }

    private ResultActions anularConsulta(String token, long consultaId, String cuerpo) throws Exception {
        return mockMvc.perform(post("/consultas/{id}/anular", consultaId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private ResultActions anularReceta(String token, long recetaId) throws Exception {
        return mockMvc.perform(post("/prescripciones/{id}/anular", recetaId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Dosis mal capturada en la receta\"}"));
    }

    private ResultActions aprobar(String token, long solicitudId) throws Exception {
        return mockMvc.perform(post("/anulaciones/{id}/aprobar", solicitudId).header("Authorization", bearer(token)));
    }

    private ResultActions rechazar(String token, long solicitudId) throws Exception {
        return mockMvc.perform(post("/anulaciones/{id}/rechazar", solicitudId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"La consulta es correcta, no procede\"}"));
    }

    private ResultActions cambiarParametros(String token, String tipo, String cuerpo) throws Exception {
        return mockMvc.perform(put("/anulaciones/parametros/{tipo}", tipo)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private ResultActions leerConsulta(long consultaId) throws Exception {
        return mockMvc.perform(get("/consultas/{id}", consultaId).header("Authorization", bearer(medico)))
                .andExpect(status().isOk());
    }

    private JsonNode cuerpo(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }
}
