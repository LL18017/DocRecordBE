package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-06 criterio 4 (estado del paciente) y HU-07 criterio 3 (busqueda sin
 * tildes).
 *
 * Las dos historias se prueban juntas porque comparten el montaje -- un
 * paciente con nombre acentuado -- y porque la segunda devuelve lo que fija la
 * primera: el estado viaja en la misma respuesta que entrega la busqueda.
 */
class EstadoYBusquedaDePacientesIT extends PruebaClinica {

    /** Da de alta un paciente con nombre y apellido a medida, y devuelve su id. */
    private long crearPacienteLlamado(String token, String nombres, String apellidos) throws Exception {
        String cuerpo = mockMvc.perform(post("/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"persona":{"dui":"%s","nombres":"%s","apellidos":"%s",
                                            "fechaNacimiento":"1990-05-20","sexo":"F"},
                                 "tipoSangre":"O+"}
                                """.formatted(duiUnico(), nombres, apellidos)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("personaId").asLong();
    }

    private JsonNode buscar(String token, String texto) throws Exception {
        String cuerpo = mockMvc.perform(get("/pacientes").param("buscar", texto)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private boolean apareceEnLaBusqueda(JsonNode resultados, long personaId) {
        for (JsonNode p : resultados) {
            if (p.get("personaId").asLong() == personaId) return true;
        }
        return false;
    }

    private JsonNode leer(String token, long pacienteId) throws Exception {
        return json.readTree(
                mockMvc.perform(get("/pacientes/{id}", pacienteId)
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
    }

    // ══════════════════════════════════════════════════════════════════════
    // HU-06 criterio 4 · estado
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("un paciente recien creado nace con estado ACTIVO y con expediente")
    void naceActivo() throws Exception {
        String token = tokenDeMedico();

        String cuerpo = mockMvc.perform(post("/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"persona":{"dui":"%s","nombres":"Recien","apellidos":"Creada",
                                            "fechaNacimiento":"1990-05-20","sexo":"F"},
                                 "tipoSangre":"O+"}
                                """.formatted(duiUnico())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode creado = json.readTree(cuerpo);
        assertEquals("ACTIVO", creado.get("estado").asText(),
                "el alta debe fijar el estado, no dejarlo al azar");
        assertTrue(creado.get("expediente").asText().startsWith("EXP-"),
                "el expediente lo genera el sistema con su propio formato");
    }

    @Test
    @DisplayName("dar de baja cambia el estado pero no borra el expediente")
    void darDeBajaNoBorra() throws Exception {
        String token = tokenDeMedico();
        long pacienteId = crearPacienteLlamado(token, "Paciente", "Que Se Retira");
        String expediente = leer(token, pacienteId).get("expediente").asText();

        String cuerpo = mockMvc.perform(patch("/pacientes/{id}/estado", pacienteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"INACTIVO\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals("INACTIVO", json.readTree(cuerpo).get("estado").asText());

        // Lo que de verdad afirma el criterio: el expediente sigue ahi, intacto.
        JsonNode releido = leer(token, pacienteId);
        assertEquals(expediente, releido.get("expediente").asText(),
                "dar de baja no toca el expediente");
        assertEquals("INACTIVO", releido.get("estado").asText());
    }

    @Test
    @DisplayName("un estado inexistente se rechaza con 400, no con un 500 de la base")
    void estadoInvalidoSeRechaza() throws Exception {
        // Sin la comprobacion en el servicio, el CHECK de V12 saltaria como un
        // error de integridad y saldria un 500 que no le dice nada a nadie.
        String token = tokenDeMedico();
        long pacienteId = crearPacienteLlamado(token, "Paciente", "Con Estado Raro");

        mockMvc.perform(patch("/pacientes/{id}/estado", pacienteId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"DE VACACIONES\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("enfermeria no puede dar de baja a un paciente")
    void enfermeriaNoDaDeBaja() throws Exception {
        // Registrar y consultar si le tocan a enfermeria; decidir que un
        // paciente deja de estar en seguimiento es administrativo, no clinico.
        String medico = tokenDeMedico();
        long pacienteId = crearPacienteLlamado(medico, "Paciente", "Protegida");

        mockMvc.perform(patch("/pacientes/{id}/estado", pacienteId)
                        .header("Authorization", "Bearer " + tokenDeEnfermera())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"INACTIVO\"}"))
                .andExpect(status().isForbidden());
    }

    // ══════════════════════════════════════════════════════════════════════
    // HU-07 criterio 3 · busqueda sin tildes
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("buscar sin tildes encuentra al paciente que si las tiene")
    void sinTildesEncuentraConTildes() throws Exception {
        String token = tokenDeMedico();
        long conTildes = crearPacienteLlamado(token, "José María", "Martínez Peña");

        // El caso real de la historia: se teclea deprisa, con el paciente
        // enfrente, y nadie pone las tildes.
        assertTrue(apareceEnLaBusqueda(buscar(token, "Martinez"), conTildes),
                "Martinez debe encontrar a Martínez");
        assertTrue(apareceEnLaBusqueda(buscar(token, "Jose"), conTildes),
                "Jose debe encontrar a José");
        assertTrue(apareceEnLaBusqueda(buscar(token, "Pena"), conTildes),
                "Pena debe encontrar a Peña");
    }

    @Test
    @DisplayName("buscar CON tildes tambien encuentra, y sigue ignorando mayusculas")
    void conTildesYMayusculasTambien() throws Exception {
        // La otra direccion importa igual: normalizar solo el texto tecleado
        // habria roto este caso, porque lo guardado conserva sus tildes.
        String token = tokenDeMedico();
        long conTildes = crearPacienteLlamado(token, "Ángela", "Ramírez Hernández");

        assertTrue(apareceEnLaBusqueda(buscar(token, "Ramírez"), conTildes));
        assertTrue(apareceEnLaBusqueda(buscar(token, "RAMIREZ"), conTildes));
        assertTrue(apareceEnLaBusqueda(buscar(token, "angela"), conTildes));
    }

    @Test
    @DisplayName("una busqueda sin coincidencias devuelve la lista vacia, no un error")
    void sinCoincidencias() throws Exception {
        // Con la consulta partida en dos, la lista vacia ya no llega a la
        // segunda: un IN con cero elementos no es SQL valido.
        String token = tokenDeMedico();
        JsonNode resultados = buscar(token, "zzz-no-existe-ningun-paciente-asi");

        assertTrue(resultados.isArray(), "debe seguir siendo una lista");
        assertEquals(0, resultados.size(), "y estar vacia");
    }

    @Test
    @DisplayName("la busqueda sin texto devuelve a todos, no a ninguno")
    void sinTextoDevuelveTodos() throws Exception {
        String token = tokenDeMedico();
        long creado = crearPacienteLlamado(token, "Paciente", "Del Listado Completo");

        assertTrue(apareceEnLaBusqueda(buscar(token, ""), creado));
        assertFalse(buscar(token, "").isEmpty());
    }
}
