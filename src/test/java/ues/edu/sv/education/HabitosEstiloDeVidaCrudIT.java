
package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// **
// * Pruebas de integración de hábitos de estilo de vida y tipos de hábito.
// *
// * Utiliza la API real, autenticación JWT y datos creados durante cada prueba.
// */
class HabitosEstiloDeVidaCrudIT extends PruebaClinica {

    private static final String BASE = "/api/habitos-estilo-de-vida";
    private static final long ID_INEXISTENTE = 999999999L;
    private static final int TIPO_INEXISTENTE = 999999999;

    private String medico;
    private String enfermera;
    private String admin;
    private long pacienteId;
    private int tipoHabitoId;

    @BeforeEach
    void prepararDatos() throws Exception {
        medico = tokenDeMedico();
        enfermera = tokenDeEnfermera();
        admin = tokenDeAdministrador();

        pacienteId = crearPaciente(medico);
        tipoHabitoId = crearTipoHabito(admin,
                "Tipo de prueba " + siguiente())
                .get("id").asInt();
    }

    // ================================================================
    // Hábitos: creación
    // ================================================================

    @Test
    @DisplayName("el médico puede crear un hábito y recibe sus datos")
    void crearHabitoCorrectamente() throws Exception {
        JsonNode respuesta = crearHabito(medico, solicitudHabito(
                "Camina diariamente", "MODERADO",
                pacienteId, tipoHabitoId));

        assertNotNull(respuesta.get("id"));
        assertEquals("Camina diariamente",
                respuesta.get("descripcion").asText());
        assertEquals("MODERADO", respuesta.get("nivel").asText());
        assertEquals(pacienteId,
                respuesta.get("pacienteId").asLong());
        assertEquals(tipoHabitoId,
                respuesta.get("tipoHabito").get("id").asInt());
    }

    @Test
    @DisplayName("la enfermera también puede crear un hábito")
    void enfermeraPuedeCrearHabito() throws Exception {
        crearHabito(enfermera, solicitudHabito(
                "Mantiene una dieta equilibrada", "ALTO",
                pacienteId, tipoHabitoId));
    }

    @Test
    @DisplayName("un administrador no puede crear hábitos")
    void administradorNoPuedeCrearHabito() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solicitudHabito(
                                "Hábito no autorizado", "BAJO",
                                pacienteId, tipoHabitoId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("la descripción vacía se rechaza")
    void descripcionVaciaSeRechaza() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solicitudHabito(
                                "   ", "BAJO",
                                pacienteId, tipoHabitoId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("no se crea un hábito si el paciente no existe")
    void pacienteInexistenteNoPermiteCrearHabito() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solicitudHabito(
                                "Hábito de prueba", "BAJO",
                                ID_INEXISTENTE, tipoHabitoId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("no se crea un hábito si el tipo no existe")
    void tipoInexistenteNoPermiteCrearHabito() throws Exception {
        mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solicitudHabito(
                                "Hábito de prueba", "BAJO",
                                pacienteId, TIPO_INEXISTENTE)))
                .andExpect(status().isNotFound());
    }

    // ================================================================
    // Hábitos: consultas
    // ================================================================

    @Test
    @DisplayName("el listado contiene el hábito creado")
    void listarHabitosIncluyeElCreado() throws Exception {
        JsonNode creado = crearHabito(medico, solicitudHabito(
                "Lectura diaria", "ALTO",
                pacienteId, tipoHabitoId));

        long id = creado.get("id").asLong();

        JsonNode listado = json.readTree(
                mockMvc.perform(get(BASE)
                                .header("Authorization", bearer(medico)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());

        assertTrue(contieneId(listado, id),
                "el listado debe incluir el hábito recién creado");
    }

    @Test
    @DisplayName("se puede obtener un hábito por su ID")
    void obtenerHabitoPorId() throws Exception {
        JsonNode creado = crearHabito(medico, solicitudHabito(
                "Ejercicio semanal", "MODERADO",
                pacienteId, tipoHabitoId));

        long id = creado.get("id").asLong();

        JsonNode respuesta = json.readTree(
                mockMvc.perform(get(BASE + "/{id}", id)
                                .header("Authorization", bearer(medico)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());

        assertEquals(id, respuesta.get("id").asLong());
        assertEquals("Ejercicio semanal",
                respuesta.get("descripcion").asText());
    }

    @Test
    @DisplayName("obtener un hábito inexistente devuelve 404")
    void obtenerHabitoInexistente() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", ID_INEXISTENTE)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("el filtro por paciente devuelve sus hábitos y excluye los de otro paciente")
    void listarHabitosPorPaciente() throws Exception {
        long otroPaciente = crearPaciente(medico);

        JsonNode delPaciente = crearHabito(medico, solicitudHabito(
                "Hábito del primer paciente", "BAJO",
                pacienteId, tipoHabitoId));

        JsonNode delOtro = crearHabito(medico, solicitudHabito(
                "Hábito del segundo paciente", "ALTO",
                otroPaciente, tipoHabitoId));

        JsonNode listado = json.readTree(
                mockMvc.perform(get(BASE + "/paciente/{pacienteId}", pacienteId)
                                .header("Authorization", bearer(medico)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());

        assertTrue(contieneId(listado, delPaciente.get("id").asLong()));
        assertFalse(contieneId(listado, delOtro.get("id").asLong()),
                "no deben aparecer hábitos de otro paciente");

        for (JsonNode habito : listado) {
            assertEquals(pacienteId, habito.get("pacienteId").asLong());
        }
    }

    @Test
    @DisplayName("filtrar por un paciente inexistente devuelve 404")
    void listarHabitosDePacienteInexistente() throws Exception {
        mockMvc.perform(get(BASE + "/paciente/{pacienteId}", ID_INEXISTENTE)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());
    }

    // ================================================================
    // Hábitos: actualización
    // ================================================================

    @Test
    @DisplayName("el médico puede actualizar un hábito")
    void actualizarHabito() throws Exception {
        JsonNode creado = crearHabito(medico, solicitudHabito(
                "Descripción original", "BAJO",
                pacienteId, tipoHabitoId));

        long id = creado.get("id").asLong();

        JsonNode actualizado = json.readTree(
                mockMvc.perform(put(BASE + "/{id}", id)
                                .header("Authorization", bearer(medico))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(solicitudHabito(
                                        "Descripción actualizada", "ALTO",
                                        pacienteId, tipoHabitoId)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());

        assertEquals(id, actualizado.get("id").asLong());
        assertEquals("Descripción actualizada",
                actualizado.get("descripcion").asText());
        assertEquals("ALTO", actualizado.get("nivel").asText());
    }

    @Test
    @DisplayName("la enfermera puede actualizar un hábito")
    void enfermeraPuedeActualizarHabito() throws Exception {
        JsonNode creado = crearHabito(medico, solicitudHabito(
                "Hábito original", "BAJO",
                pacienteId, tipoHabitoId));

        mockMvc.perform(put(BASE + "/{id}", creado.get("id").asLong())
                        .header("Authorization", bearer(enfermera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solicitudHabito(
                                "Actualizado por enfermería", "MODERADO",
                                pacienteId, tipoHabitoId)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("un administrador no puede actualizar hábitos")
    void administradorNoPuedeActualizarHabito() throws Exception {
        JsonNode creado = crearHabito(medico, solicitudHabito(
                "Hábito original", "BAJO",
                pacienteId, tipoHabitoId));

        mockMvc.perform(put(BASE + "/{id}", creado.get("id").asLong())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solicitudHabito(
                                "Cambio no autorizado", "ALTO",
                                pacienteId, tipoHabitoId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("actualizar un hábito inexistente devuelve 404")
    void actualizarHabitoInexistente() throws Exception {
        mockMvc.perform(put(BASE + "/{id}", ID_INEXISTENTE)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solicitudHabito(
                                "No debe guardarse", "BAJO",
                                pacienteId, tipoHabitoId)))
                .andExpect(status().isNotFound());
    }

    // ================================================================
    // Hábitos: eliminación
    // ================================================================

    @Test
    @DisplayName("el médico puede eliminar un hábito")
    void eliminarHabito() throws Exception {
        JsonNode creado = crearHabito(medico, solicitudHabito(
                "Hábito que será eliminado", "BAJO",
                pacienteId, tipoHabitoId));

        long id = creado.get("id").asLong();

        mockMvc.perform(delete(BASE + "/{id}", id)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/{id}", id)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("la enfermera no puede eliminar hábitos")
    void enfermeraNoPuedeEliminarHabito() throws Exception {
        JsonNode creado = crearHabito(medico, solicitudHabito(
                "Hábito protegido", "BAJO",
                pacienteId, tipoHabitoId));

        mockMvc.perform(delete(BASE + "/{id}", creado.get("id").asLong())
                        .header("Authorization", bearer(enfermera)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("eliminar un hábito inexistente devuelve 404")
    void eliminarHabitoInexistente() throws Exception {
        mockMvc.perform(delete(BASE + "/{id}", ID_INEXISTENTE)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());
    }

    // ================================================================
    // Tipos de hábito: creación
    // ================================================================

    @Test
    @DisplayName("el administrador puede crear un tipo de hábito")
    void crearTipoHabito() throws Exception {
        String descripcion = "Tipo nuevo " + siguiente();

        JsonNode creado = crearTipoHabito(admin, descripcion);

        assertNotNull(creado.get("id"));
        assertEquals(descripcion, creado.get("descripcion").asText());
    }

    @Test
    @DisplayName("la descripción vacía de un tipo se rechaza")
    void crearTipoConDescripcionVacia() throws Exception {
        mockMvc.perform(post(BASE + "/tipos")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descripcion":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("el médico no puede crear tipos de hábito")
    void medicoNoPuedeCrearTipoHabito() throws Exception {
        mockMvc.perform(post(BASE + "/tipos")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descripcion":"Tipo no autorizado"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("la enfermera no puede crear tipos de hábito")
    void enfermeraNoPuedeCrearTipoHabito() throws Exception {
        mockMvc.perform(post(BASE + "/tipos")
                        .header("Authorization", bearer(enfermera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descripcion":"Tipo no autorizado"}
                                """))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    // Tipos de hábito: consultas
    // ================================================================

    @Test
    @DisplayName("el listado de tipos contiene el tipo creado")
    void listarTiposHabito() throws Exception {
        JsonNode listado = json.readTree(
                mockMvc.perform(get(BASE + "/tipos")
                                .header("Authorization", bearer(medico)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());

        assertTrue(contieneId(listado, tipoHabitoId));
    }

    @Test
    @DisplayName("se puede obtener un tipo de hábito por su ID")
    void obtenerTipoHabito() throws Exception {
        JsonNode respuesta = json.readTree(
                mockMvc.perform(get(BASE + "/tipos/{id}", tipoHabitoId)
                                .header("Authorization", bearer(medico)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());

        assertEquals(tipoHabitoId, respuesta.get("id").asInt());
        assertNotNull(respuesta.get("descripcion"));
    }

    @Test
    @DisplayName("obtener un tipo de hábito inexistente devuelve 404")
    void obtenerTipoHabitoInexistente() throws Exception {
        mockMvc.perform(get(BASE + "/tipos/{id}", TIPO_INEXISTENTE)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());
    }

    // ================================================================
    // Tipos de hábito: actualización
    // ================================================================

    @Test
    @DisplayName("el administrador puede actualizar un tipo de hábito")
    void actualizarTipoHabito() throws Exception {
        String descripcion = "Tipo actualizado " + siguiente();

        JsonNode respuesta = json.readTree(
                mockMvc.perform(put(BASE + "/tipos/{id}", tipoHabitoId)
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"descripcion":"%s"}
                                        """.formatted(descripcion)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());

        assertEquals(tipoHabitoId, respuesta.get("id").asInt());
        assertEquals(descripcion, respuesta.get("descripcion").asText());
    }

    @Test
    @DisplayName("el médico no puede actualizar tipos de hábito")
    void medicoNoPuedeActualizarTipoHabito() throws Exception {
        mockMvc.perform(put(BASE + "/tipos/{id}", tipoHabitoId)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descripcion":"Cambio no autorizado"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("actualizar un tipo inexistente devuelve 404")
    void actualizarTipoHabitoInexistente() throws Exception {
        mockMvc.perform(put(BASE + "/tipos/{id}", TIPO_INEXISTENTE)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descripcion":"Tipo inexistente"}
                                """))
                .andExpect(status().isNotFound());
    }

    // ================================================================
    // Tipos de hábito: eliminación
    // ================================================================

    @Test
    @DisplayName("el administrador puede eliminar un tipo sin hábitos asociados")
    void eliminarTipoHabitoSinAsociaciones() throws Exception {
        JsonNode creado = crearTipoHabito(admin,
                "Tipo para eliminar " + siguiente());

        int id = creado.get("id").asInt();

        mockMvc.perform(delete(BASE + "/tipos/{id}", id)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/tipos/{id}", id)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("no se puede eliminar un tipo asociado a un hábito")
    void noEliminarTipoConHabitosAsociados() throws Exception {
        crearHabito(medico, solicitudHabito(
                "Hábito que usa el tipo", "MODERADO",
                pacienteId, tipoHabitoId));

        mockMvc.perform(delete(BASE + "/tipos/{id}", tipoHabitoId)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().is4xxClientError());

        // El tipo debe seguir disponible después del rechazo.
        mockMvc.perform(get(BASE + "/tipos/{id}", tipoHabitoId)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("el médico no puede eliminar tipos de hábito")
    void medicoNoPuedeEliminarTipoHabito() throws Exception {
        mockMvc.perform(delete(BASE + "/tipos/{id}", tipoHabitoId)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("eliminar un tipo inexistente devuelve 404")
    void eliminarTipoHabitoInexistente() throws Exception {
        mockMvc.perform(delete(BASE + "/tipos/{id}", TIPO_INEXISTENTE)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
    }

    // ================================================================
    // Métodos auxiliares
    // ================================================================

    private JsonNode crearHabito(String token, String cuerpo)
            throws Exception {
        String respuesta = mockMvc.perform(post(BASE)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return json.readTree(respuesta);
    }

    private JsonNode crearTipoHabito(String token, String descripcion)
            throws Exception {
        String respuesta = mockMvc.perform(post(BASE + "/tipos")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descripcion":"%s"}
                                """.formatted(descripcion)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return json.readTree(respuesta);
    }

    private String solicitudHabito(
            String descripcion,
            String nivel,
            long pacienteId,
            int tipoHabitoId) {

        return """
                {
                  "descripcion": "%s",
                  "nivel": "%s",
                  "pacienteId": %d,
                  "tipoHabitoId": %d
                }
                """.formatted(
                descripcion, nivel, pacienteId, tipoHabitoId);
    }

    private boolean contieneId(JsonNode listado, long id) {
        if (listado == null || !listado.isArray()) {
            return false;
        }

        for (JsonNode elemento : listado) {
            if (elemento.hasNonNull("id")
                    && elemento.get("id").asLong() == id) {
                return true;
            }
        }

        return false;
    }
}
