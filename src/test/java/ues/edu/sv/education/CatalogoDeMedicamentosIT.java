package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-23 (DRS-92): catalogo de medicamentos, a traves de la API real.
 *
 * Una prueba por criterio de aceptacion, mas los permisos (403) y los ids que
 * no existen (404).
 *
 * Cada prueba crea sus PROPIOS medicamentos, con un numero unico en el
 * nombre: la base se recrea una vez por corrida, no por prueba, y desactivar
 * un producto del catalogo sembrado romperia a PrescripcionCrudIT, que
 * receta con ellos.
 */
class CatalogoDeMedicamentosIT extends PruebaClinica {

    @Autowired private JdbcTemplate jdbc;

    private String admin;
    private String medico;

    @BeforeEach
    void prepararUsuarios() throws Exception {
        admin = tokenDeAdministrador();
        medico = tokenDeMedico();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 1: exige nombre generico, comercial, principio activo,
    // presentacion y concentracion
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("criterio 1: registrar exige los cinco datos; con todos se guarda activo")
    void registrarExigeLosCincoDatos() throws Exception {
        int n = siguiente();
        String[] campos = {"nombreGenerico", "nombreComercial", "principioActivo", "presentacion", "concentracion"};

        // Uno por uno: falta cada campo (o viene en blanco) y se rechaza. En
        // blanco cuenta como ausente porque "   " no le dice nada a quien
        // surte la receta.
        for (String falta : campos) {
            for (String valor : new String[]{null, "   "}) {
                mockMvc.perform(post("/medicamentos")
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(cuerpoSin(falta, valor, n)))
                        .andExpect(status().isBadRequest());
            }
        }
        assertEquals(0, filasConComercial("Comercial " + n), "una peticion rechazada dejo una fila");

        JsonNode creado = registrar(admin, "Generico " + n, "Comercial " + n, "Principio " + n, "Tableta", "10 mg");
        assertNotNull(creado.get("medicamentoId"));
        assertEquals("Generico " + n, creado.get("nombreGenerico").asText());
        assertEquals("Comercial " + n, creado.get("nombreComercial").asText());
        assertEquals("Principio " + n, creado.get("principioActivo").asText());
        assertEquals("Tableta", creado.get("presentacion").asText());
        assertEquals("10 mg", creado.get("concentracion").asText());
        assertTrue(creado.get("activo").asBoolean(), "un medicamento nuevo nace activo");

        // El principio activo queda como dato propio, no dentro del nombre:
        // es lo que HU-24 va a cruzar contra las alergias.
        String enLaBase = jdbc.queryForObject(
                "SELECT principio_activo FROM medicamentos WHERE medicamento_id = ?",
                String.class, creado.get("medicamentoId").asLong());
        assertEquals("Principio " + n, enLaBase);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 2: el duplicado se rechaza diciendo cual ya existe
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("criterio 2: mismo comercial, presentacion y concentracion -sin importar mayusculas, tildes ni espacios- da 409 nombrando al existente")
    void elDuplicadoSeRechazaNombrandoAlExistente() throws Exception {
        int n = siguiente();
        JsonNode original = registrar(admin, "Acetaminofén", "Analgésico " + n, "Paracetamol", "Cápsula", "500 mg");
        long originalId = original.get("medicamentoId").asLong();

        // Mismo producto escrito de otra forma: mayusculas, sin tildes, sin
        // el espacio de la concentracion. El generico distinto no lo salva: la
        // clave son los tres campos del criterio.
        String mensaje = conflicto(post("/medicamentos"), cuerpo(
                "Paracetamol", "ANALGESICO " + n, "Paracetamol", "capsula", "500MG"));
        assertTrue(mensaje.contains("Analgésico " + n), "el 409 no dice cual es el existente: " + mensaje);
        assertTrue(mensaje.contains(String.valueOf(originalId)), "el 409 no da el codigo del existente: " + mensaje);
        assertEquals(1, filasConComercial("Analgésico " + n));

        // Otra concentracion SI es otro producto.
        registrar(admin, "Acetaminofén", "Analgésico " + n, "Paracetamol", "Cápsula", "1 g");

        // Editar uno hasta chocar con otro tambien se rechaza...
        JsonNode otro = registrar(admin, "Acetaminofén", "Analgésico " + n, "Paracetamol", "Jarabe", "160 mg/5 mL");
        String alEditar = conflicto(put("/medicamentos/{id}", otro.get("medicamentoId").asLong()), cuerpo(
                "Acetaminofén", "Analgésico " + n, "Paracetamol", "Cápsula", "500 mg"));
        assertTrue(alEditar.contains(String.valueOf(originalId)), "al editar, el 409 no nombra al existente: " + alEditar);

        // ...pero guardarse a si mismo sin cambiar la clave no es un duplicado.
        mockMvc.perform(put("/medicamentos/{id}", originalId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Acetaminofén", "Analgésico " + n, "Paracetamol", "Cápsula", "500 mg")))
                .andExpect(status().isOk());

        // Si el existente esta desactivado, el mensaje lo dice: lo que se
        // quiere entonces es reactivarlo, no tener dos filas del mismo producto.
        cambiarEstado(admin, originalId, false).andExpect(status().isOk());
        String mensajeInactivo = conflicto(post("/medicamentos"), cuerpo(
                "Acetaminofén", "Analgésico " + n, "Paracetamol", "Cápsula", "500 mg"));
        assertTrue(mensajeInactivo.toLowerCase().contains("desactivado"), mensajeInactivo);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 3: la receta se escribe desde el catalogo y no acepta nada
    // fuera de el
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("criterio 3: el catalogo se busca sin tildes ni mayusculas, y la receta rechaza lo que no esta en el")
    void laRecetaSoloAceptaLoDelCatalogo() throws Exception {
        int n = siguiente();
        JsonNode med = registrar(admin, "Genérico Búsqueda " + n, "Fármaco Único " + n,
                "Ácido Prueba " + n, "Tableta", "25 mg");
        long id = med.get("medicamentoId").asLong();

        // El autocompletado encuentra el producto por cualquiera de sus tres
        // nombres, tecleado deprisa: sin tildes y en minusculas.
        for (String tecleado : new String[]{"farmaco unico " + n, "generico busqueda " + n, "acido prueba " + n}) {
            assertTrue(idsDe(listar(medico, tecleado, false)).contains(id),
                    "'" + tecleado + "' no encontro el medicamento");
        }
        // Y la enfermeria tambien lo lee.
        mockMvc.perform(get("/medicamentos").param("buscar", "farmaco unico " + n)
                        .header("Authorization", bearer(tokenDeEnfermera())))
                .andExpect(status().isOk());

        long consultaId = crearConsultaSimple(medico, crearPaciente(medico)).get("consultaId").asLong();

        // Un id que no esta en el catalogo: 400 y no se guarda nada.
        mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consultaId":%d,"medicamentos":[{"medicamentoId":9999999}]}
                                """.formatted(consultaId)))
                .andExpect(status().isBadRequest());

        // Texto libre, como antes del catalogo: tampoco.
        mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consultaId":%d,"medicamentos":[{"medicamento":"Cualquier cosa"}]}
                                """.formatted(consultaId)))
                .andExpect(status().isBadRequest());

        assertEquals(0, recetasDeLaConsulta(consultaId).size(), "una receta rechazada quedo guardada");

        // Con el del catalogo si, y el nombre de la receta lo pone el servidor.
        JsonNode receta = emitir(consultaId, id);
        JsonNode renglon = receta.get("medicamentos").get(0);
        assertEquals(id, renglon.get("medicamentoId").asLong());
        assertEquals("Genérico Búsqueda " + n + " 25 mg (Fármaco Único " + n + "), Tableta",
                renglon.get("medicamento").asText());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 4: desactivado ya no se ofrece ni se acepta, pero las recetas
    // anteriores lo siguen mostrando
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("criterio 4: un medicamento desactivado sale de la lista y de las recetas nuevas, no de las emitidas")
    void desactivarLoSacaDeLasRecetasNuevasNoDeLasEmitidas() throws Exception {
        int n = siguiente();
        long id = registrar(admin, "Retirado " + n, "Retirado " + n, "Retirado " + n, "Tableta", "5 mg")
                .get("medicamentoId").asLong();
        long consultaId = crearConsultaSimple(medico, crearPaciente(medico)).get("consultaId").asLong();
        long recetaAnterior = emitir(consultaId, id).get("prescripcionId").asLong();

        JsonNode desactivado = json.readTree(cambiarEstado(admin, id, false)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertFalse(desactivado.get("activo").asBoolean());

        // Ya no aparece como opcion para recetar...
        assertFalse(idsDe(listar(medico, "retirado " + n, false)).contains(id),
                "el autocompletado sigue ofreciendo un medicamento desactivado");
        // ...ni se acepta si alguien manda su id igual.
        mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consultaId":%d,"medicamentos":[{"medicamentoId":%d}]}
                                """.formatted(consultaId, id)))
                .andExpect(status().isBadRequest());
        assertEquals(1, recetasDeLaConsulta(consultaId).size());

        // La receta de antes sigue mostrandolo, con su nombre y su referencia.
        JsonNode anterior = json.readTree(mockMvc.perform(get("/prescripciones/{id}", recetaAnterior)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode renglon = anterior.get("medicamentos").get(0);
        assertEquals(id, renglon.get("medicamentoId").asLong());
        assertEquals("Retirado " + n + " 5 mg (Retirado " + n + "), Tableta", renglon.get("medicamento").asText());

        // El administrador lo sigue viendo -para poder reactivarlo-, y nunca
        // se borro la fila.
        JsonNode conInactivos = listar(admin, "retirado " + n, true);
        assertTrue(idsDe(conInactivos).contains(id));
        assertEquals(1, filasConComercial("Retirado " + n));

        // Reactivado, vuelve a recetarse.
        cambiarEstado(admin, id, true).andExpect(status().isOk());
        assertTrue(idsDe(listar(medico, "retirado " + n, false)).contains(id));
        emitir(consultaId, id);
    }

    @Test
    @DisplayName("editar el catalogo no reescribe las recetas que ya se emitieron")
    void editarElCatalogoNoCambiaElHistorico() throws Exception {
        int n = siguiente();
        long id = registrar(admin, "Original " + n, "Original " + n, "Original " + n, "Tableta", "5 mg")
                .get("medicamentoId").asLong();
        long consultaId = crearConsultaSimple(medico, crearPaciente(medico)).get("consultaId").asLong();
        long receta = emitir(consultaId, id).get("prescripcionId").asLong();

        mockMvc.perform(put("/medicamentos/{id}", id)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Corregido " + n, "Original " + n, "Original " + n, "Tableta", "10 mg")))
                .andExpect(status().isOk());

        JsonNode despues = json.readTree(mockMvc.perform(get("/prescripciones/{id}", receta)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals("Original " + n + " 5 mg (Original " + n + "), Tableta",
                despues.get("medicamentos").get(0).get("medicamento").asText(),
                "la receta entregada cambio de nombre porque cambio el catalogo");
    }

    @Test
    @DisplayName("las recetas anteriores al catalogo -texto libre, sin medicamento_id- se siguen leyendo igual")
    void lasRecetasDeTextoLibreSeSiguenLeyendo() throws Exception {
        JsonNode consulta = crearConsultaSimple(medico, crearPaciente(medico));
        long consultaId = consulta.get("consultaId").asLong();
        long medicoId = consulta.get("medico").get("personaId").asLong();

        // Se inserta a mano porque la API ya no deja crearlas: es el estado en
        // que quedaron las recetas emitidas antes de V22.
        KeyHolder llave = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO prescripciones (consulta_id, medico_id, fecha) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, consultaId);
            ps.setLong(2, medicoId);
            ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, llave);
        long recetaId = ((Number) llave.getKeys().get("prescripcion_id")).longValue();
        jdbc.update("INSERT INTO prescripcion_medicamentos (prescripcion_id, medicamento, dosis) VALUES (?, ?, ?)",
                recetaId, "amoxicilina 500", "1 cada 8 h");

        JsonNode receta = json.readTree(mockMvc.perform(get("/prescripciones/{id}", recetaId)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode renglon = receta.get("medicamentos").get(0);
        assertEquals("amoxicilina 500", renglon.get("medicamento").asText());
        assertEquals("1 cada 8 h", renglon.get("dosis").asText());
        assertTrue(renglon.get("medicamentoId").isNull());

        // Y tambien en el listado.
        assertEquals(1, recetasDeLaConsulta(consultaId).size());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Permisos y 404
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("403: solo el administrador escribe en el catalogo y ve los desactivados")
    void soloElAdministradorEscribe() throws Exception {
        int n = siguiente();
        long id = registrar(admin, "Permiso " + n, "Permiso " + n, "Permiso " + n, "Tableta", "5 mg")
                .get("medicamentoId").asLong();
        String enfermera = tokenDeEnfermera();
        String alta = cuerpo("Intruso " + n, "Intruso " + n, "Intruso " + n, "Tableta", "5 mg");

        for (String token : new String[]{medico, enfermera}) {
            mockMvc.perform(post("/medicamentos").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(alta))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/medicamentos/{id}", id).header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(alta))
                    .andExpect(status().isForbidden());
            cambiarEstado(token, id, false).andExpect(status().isForbidden());
            mockMvc.perform(get("/medicamentos").param("incluirInactivos", "true")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
        }

        assertEquals(0, filasConComercial("Intruso " + n));
        JsonNode intacto = json.readTree(mockMvc.perform(get("/medicamentos/{id}", id)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals("Permiso " + n, intacto.get("nombreGenerico").asText());
        assertTrue(intacto.get("activo").asBoolean());

        // Sin sesion, ni leer.
        mockMvc.perform(get("/medicamentos")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("404: un medicamento que no existe no se ve, no se edita ni se desactiva")
    void unMedicamentoInexistenteResponde404() throws Exception {
        mockMvc.perform(get("/medicamentos/{id}", 9999999).header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/medicamentos/{id}", 9999999).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("No", "Existe", "Nada", "Tableta", "1 mg")))
                .andExpect(status().isNotFound());
        cambiarEstado(admin, 9999999, false).andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    private String cuerpo(String generico, String comercial, String principio, String presentacion,
                          String concentracion) throws Exception {
        var nodo = json.createObjectNode();
        nodo.put("nombreGenerico", generico);
        nodo.put("nombreComercial", comercial);
        nodo.put("principioActivo", principio);
        nodo.put("presentacion", presentacion);
        nodo.put("concentracion", concentracion);
        return json.writeValueAsString(nodo);
    }

    /** Un cuerpo completo salvo `falta`, que va con `valor` (null la omite). */
    private String cuerpoSin(String falta, String valor, int n) throws Exception {
        var nodo = json.readTree(cuerpo("Generico " + n, "Comercial " + n, "Principio " + n, "Tableta", "10 mg"));
        var objeto = (com.fasterxml.jackson.databind.node.ObjectNode) nodo;
        if (valor == null) objeto.remove(falta);
        else objeto.put(falta, valor);
        return json.writeValueAsString(objeto);
    }

    private JsonNode registrar(String token, String generico, String comercial, String principio,
                               String presentacion, String concentracion) throws Exception {
        return json.readTree(mockMvc.perform(post("/medicamentos")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(generico, comercial, principio, presentacion, concentracion)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    /** Hace la peticion como admin, exige 409 y devuelve el `message`. */
    private String conflicto(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
                             String cuerpoJson) throws Exception {
        String respuesta = mockMvc.perform(peticion
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoJson))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return json.readTree(respuesta).get("message").asText();
    }

    private org.springframework.test.web.servlet.ResultActions cambiarEstado(String token, long id, boolean activo)
            throws Exception {
        return mockMvc.perform(patch("/medicamentos/{id}/estado", id)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\":" + activo + "}"));
    }

    private JsonNode listar(String token, String buscar, boolean incluirInactivos) throws Exception {
        return json.readTree(mockMvc.perform(get("/medicamentos")
                        .param("buscar", buscar)
                        .param("incluirInactivos", String.valueOf(incluirInactivos))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private List<Long> idsDe(JsonNode lista) {
        List<Long> ids = new ArrayList<>();
        lista.forEach(m -> ids.add(m.get("medicamentoId").asLong()));
        return ids;
    }

    private JsonNode emitir(long consultaId, long medicamentoId) throws Exception {
        return json.readTree(mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consultaId":%d,"medicamentos":[{"medicamentoId":%d,"dosis":"1 tableta"}]}
                                """.formatted(consultaId, medicamentoId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private JsonNode recetasDeLaConsulta(long consultaId) throws Exception {
        return json.readTree(mockMvc.perform(get("/prescripciones")
                        .param("consultaId", String.valueOf(consultaId))
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("contenido");
    }

    private int filasConComercial(String nombreComercial) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM medicamentos WHERE nombre_comercial = ?", Integer.class, nombreComercial);
        return total == null ? 0 : total;
    }
}
