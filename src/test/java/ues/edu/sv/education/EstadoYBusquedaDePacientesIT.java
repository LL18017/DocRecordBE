package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
                        .header("Authorization", "Bearer " + tokenDeAdministrador())
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
                        .header("Authorization", "Bearer " + tokenDeAdministrador())
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

    @Test
    @DisplayName("enfermeria tampoco da de baja por DELETE: las dos rutas exigen lo mismo")
    void lasDosRutasDeBajaExigenElMismoRol() throws Exception {
        // Este agujero lo ABRIO el arreglo de la baja logica, no lo heredo.
        //
        // Mientras DELETE destruia la fila y PATCH cambiaba el estado eran
        // operaciones distintas, y que el DELETE heredara el alcance de la clase
        // -- que incluye ENFERMERA -- era feo pero discutible. Al hacer que las
        // DOS pongan INACTIVO paso a ser una ruta que evade la politica: en el
        // despliegue, enfermeria recibia 403 en el PATCH y 204 en el DELETE,
        // sobre el mismo paciente y con el mismo efecto.
        //
        // No se comprueba un codigo concreto sino que los dos caminos respondan
        // IGUAL. Si manana se decide abrir o cerrar la baja a otro rol, quien la
        // cambie en un sitio y se olvide del otro rompe esta prueba, que es
        // exactamente lo que hace falta que ocurra.
        String medico = tokenDeMedico();
        long pacienteId = crearPacienteLlamado(medico, "Paciente", "Con Dos Puertas");
        String enfermera = tokenDeEnfermera();

        int porPatch = mockMvc.perform(patch("/pacientes/{id}/estado", pacienteId)
                        .header("Authorization", "Bearer " + enfermera)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"INACTIVO\"}"))
                .andReturn().getResponse().getStatus();

        int porDelete = mockMvc.perform(delete("/pacientes/{id}", pacienteId)
                        .header("Authorization", "Bearer " + enfermera))
                .andReturn().getResponse().getStatus();

        assertEquals(porPatch, porDelete,
                "las dos rutas dejan al paciente INACTIVO: no pueden exigir permisos distintos");
        assertEquals(403, porDelete,
                "enfermeria registra y consulta; decidir que un paciente sale de seguimiento no le toca");

        // Y lo que de verdad importa: el paciente sigue activo.
        assertEquals("ACTIVO", leer(medico, pacienteId).get("estado").asText(),
                "el intento rechazado no debe haber cambiado nada");
    }

    @Test
    @DisplayName("el medico TAMPOCO da de baja: es potestad del administrador")
    void elMedicoTampocoDaDeBaja() throws Exception {
        // La regla tenia tres versiones: el codigo decia ADMIN y MEDICO, la
        // herencia de la clase dejaba pasar ademas a ENFERMERA por el DELETE, y
        // la Tabla 5 del Laboratorio 2 decia que el administrador es el unico
        // rol que da de baja pacientes. Se resolvio a favor del documento, que
        // ademas coincide con el actor de la propia HU-10: "Como administrador
        // de la clinica quiero marcar a un paciente como inactivo".
        String medico = tokenDeMedico();
        long pacienteId = crearPacienteLlamado(medico, "Paciente", "Solo Del Admin");

        int porPatch = mockMvc.perform(patch("/pacientes/{id}/estado", pacienteId)
                        .header("Authorization", "Bearer " + medico)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"INACTIVO\"}"))
                .andReturn().getResponse().getStatus();

        int porDelete = mockMvc.perform(delete("/pacientes/{id}", pacienteId)
                        .header("Authorization", "Bearer " + medico))
                .andReturn().getResponse().getStatus();

        assertEquals(porPatch, porDelete, "las dos rutas tienen que exigir lo mismo");
        assertEquals(403, porDelete);
        assertEquals("ACTIVO", leer(medico, pacienteId).get("estado").asText(),
                "el intento rechazado no debe haber cambiado nada");
    }

    @Test
    @DisplayName("el administrador SI puede dar de baja, por las dos rutas")
    void elAdministradorSiPuedeDarDeBaja() throws Exception {
        // Contrapeso imprescindible: una prueba que solo mira el 403 se sigue
        // cumpliendo si alguien cierra la baja a todo el mundo.
        String medico = tokenDeMedico();
        long pacienteId = crearPacienteLlamado(medico, "Paciente", "Del Administrador");

        darDeBaja(pacienteId);
        assertEquals("INACTIVO", leer(medico, pacienteId).get("estado").asText());

        mockMvc.perform(patch("/pacientes/{id}/estado", pacienteId)
                        .header("Authorization", "Bearer " + tokenDeAdministrador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ACTIVO\"}"))
                .andExpect(status().isOk());
    }

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

    // ══════════════════════════════════════════════════════════════════════
    // HU-08 criterio 4 · el paciente de baja sale del listado de trabajo
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("un paciente dado de baja no aparece en la busqueda normal")
    void elInactivoNoApareceEnLaBusquedaNormal() throws Exception {
        String token = tokenDeMedico();
        long pacienteId = crearPacienteLlamado(token, "Paciente", "Fuera Del Listado");

        assertTrue(apareceEnLaBusqueda(buscar(token, ""), pacienteId),
                "mientras esta activo tiene que aparecer");

        darDeBaja(pacienteId);

        assertFalse(apareceEnLaBusqueda(buscar(token, ""), pacienteId),
                "tras la baja no puede seguir en el listado de trabajo diario");
    }

    @Test
    @DisplayName("el filtro «incluir inactivos» lo vuelve a mostrar, y como INACTIVO")
    void elFiltroDevuelveAlInactivo() throws Exception {
        String token = tokenDeMedico();
        long pacienteId = crearPacienteLlamado(token, "Paciente", "Que Reaparece");
        darDeBaja(pacienteId);

        JsonNode conInactivos = buscarIncluyendoInactivos(token, "");
        assertTrue(apareceEnLaBusqueda(conInactivos, pacienteId),
                "con el filtro activo el paciente de baja tiene que volver a salir");

        for (JsonNode p : conInactivos) {
            if (p.get("personaId").asLong() == pacienteId) {
                assertEquals("INACTIVO", p.get("estado").asText(),
                        "y tiene que venir marcado como inactivo, no disfrazado de activo");
            }
        }
    }

    @Test
    @DisplayName("DELETE da de baja: no borra el expediente ni al paciente")
    void deleteEsBajaLogica() throws Exception {
        // Antes DELETE hacia `repository.delete(...)`, un borrado fisico que
        // contradecia a HU-10 -- y se llevaba por delante el numero de
        // expediente, que es unico y correlativo.
        String token = tokenDeMedico();
        long pacienteId = crearPacienteLlamado(token, "Paciente", "Dado De Baja");
        String expediente = leer(token, pacienteId).get("expediente").asText();

        darDeBaja(pacienteId);

        JsonNode releido = leer(token, pacienteId);
        assertEquals("INACTIVO", releido.get("estado").asText());
        assertEquals(expediente, releido.get("expediente").asText(),
                "el expediente tiene que sobrevivir a la baja");
    }

    @Test
    @DisplayName("dar de baja dos veces no falla: la operacion es idempotente")
    void laBajaEsIdempotente() throws Exception {
        String token = tokenDeMedico();
        long pacienteId = crearPacienteLlamado(token, "Paciente", "De Baja Dos Veces");

        darDeBaja(pacienteId);
        darDeBaja(pacienteId);

        assertEquals("INACTIVO", leer(token, pacienteId).get("estado").asText());
    }

    @Test
    @DisplayName("readmitir a un paciente lo devuelve al listado con su mismo expediente")
    void readmitirDevuelveAlListado() throws Exception {
        // Es la contraparte de la baja logica: si el expediente sobrevive, el
        // paciente tiene que poder volver sin estrenar uno nuevo.
        String token = tokenDeMedico();
        long pacienteId = crearPacienteLlamado(token, "Paciente", "Que Regresa");
        String expediente = leer(token, pacienteId).get("expediente").asText();

        darDeBaja(pacienteId);
        mockMvc.perform(patch("/pacientes/{id}/estado", pacienteId)
                        .header("Authorization", "Bearer " + tokenDeAdministrador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ACTIVO\"}"))
                .andExpect(status().isOk());

        assertTrue(apareceEnLaBusqueda(buscar(token, ""), pacienteId));
        assertEquals(expediente, leer(token, pacienteId).get("expediente").asText());
    }

    /**
     * Da de baja al paciente como ADMINISTRADOR.
     *
     * No recibe token a proposito: desde que la baja es exclusiva de ese rol,
     * pasarle uno invitaria a escribir pruebas que la intentan con el rol
     * equivocado sin querer. Quien quiera probar el rol lo hace explicito.
     */
    private void darDeBaja(long pacienteId) throws Exception {
        mockMvc.perform(delete("/pacientes/{id}", pacienteId)
                        .header("Authorization", "Bearer " + tokenDeAdministrador()))
                .andExpect(status().isNoContent());
    }

    private JsonNode buscarIncluyendoInactivos(String token, String texto) throws Exception {
        String cuerpo = mockMvc.perform(get("/pacientes")
                        .param("buscar", texto)
                        .param("incluirInactivos", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }
}
