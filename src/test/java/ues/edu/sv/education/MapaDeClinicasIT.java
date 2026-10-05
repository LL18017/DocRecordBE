package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-28 (DRS-96) · Mapa de clinicas de la red.
 *
 *   criterio 1. un marcador por cada clinica ACTIVA.
 *   criterio 2. cada marcador muestra nombre, direccion, telefono y horario.
 *   criterio 3. se filtra por departamento.
 *   criterio 4. con 50 clinicas el mapa carga en menos de 3 segundos.
 *
 * El criterio 5 (zoom y arrastre en el telefono) es de la pagina y se prueba
 * en el frontend.
 *
 * La base es compartida entre clases y el alta commitea, asi que estas pruebas
 * nunca cuentan "todas las clinicas": cada una inventa un departamento propio
 * y pregunta solo por el. Contar el total haria que la prueba dependiera de
 * cuantas sedes dejaron las demas clases.
 */
class MapaDeClinicasIT extends PruebaClinica {

    /** Lo unico que el mapa publica de una clinica. Ni el dueño ni el estado. */
    private static final Set<String> CAMPOS_PUBLICOS = Set.of(
            "clinicaId", "name", "latitud", "longitud",
            "departamento", "municipio", "direccion", "telefono", "horario");

    private String departamentoUnico() {
        return "Depto Mapa " + siguiente();
    }

    private int crearClinica(String token, String nombre, String departamento, String coordenadas)
            throws Exception {
        String cuerpo = mockMvc.perform(post("/clinics")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","departamento":"%s","municipio":"Municipio %d",
                                 "direccion":"Calle El Progreso 12","telefono":"2440-5566",
                                 "horario":"Lunes a viernes, 7:00 a 16:00"%s}
                                """.formatted(nombre, departamento, siguiente(), coordenadas)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("clinicaId").asInt();
    }

    private static final String EN_SANTA_ANA = ",\"latitud\":13.9942,\"longitud\":-89.5597";

    private JsonNode publicas(String departamento) throws Exception {
        String cuerpo = mockMvc.perform(get("/clinics/publicas").param("departamento", departamento))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private List<String> nombres(JsonNode lista) {
        List<String> nombres = new ArrayList<>();
        lista.forEach(c -> nombres.add(c.get("name").asText()));
        return nombres;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Publico: se abre sin cuenta
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("sin token responde 200: el mapa lo abre un visitante sin cuenta")
    void sinTokenResponde() throws Exception {
        crearClinica(tokenDeMedico(), "Clinica Publica " + siguiente(), departamentoUnico(), EN_SANTA_ANA);

        String cuerpo = mockMvc.perform(get("/clinics/publicas"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode lista = json.readTree(cuerpo);
        assertTrue(lista.isArray());
        assertTrue(lista.size() >= 1, "debe haber al menos la clinica recien creada");
    }

    @Test
    @DisplayName("un token vencido o corrupto no lo rompe: la ruta publica no mira el token")
    void unTokenInvalidoNoLoRompe() throws Exception {
        // Un paciente que tuvo sesion puede llegar con un token viejo en el
        // navegador. Si el filtro lo validara aqui, el mapa le responderia 401
        // a alguien que ni siquiera necesitaba iniciar sesion.
        mockMvc.perform(get("/clinics/publicas").header("Authorization", "Bearer no-es-un-jwt"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("solo la lectura es publica: escribir en la ruta sigue pidiendo sesion")
    void soloLaLecturaEsPublica() throws Exception {
        mockMvc.perform(post("/clinics/publicas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Intrusa\"}"))
                .andExpect(status().isUnauthorized());
        // Y abrir /clinics/publicas no abre el resto de /clinics.
        mockMvc.perform(get("/clinics")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/clinics/mias")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("la respuesta se puede guardar en cache un rato: es igual para todos")
    void laRespuestaSePuedeCachear() throws Exception {
        mockMvc.perform(get("/clinics/publicas"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=60, public"));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 1 · solo las ACTIVAS
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("una clinica dada de baja no aparece en el mapa")
    void noIncluyeLasInactivas() throws Exception {
        String token = tokenDeMedico();
        String depto = departamentoUnico();
        String abierta = "Clinica Abierta " + siguiente();
        String cerrada = "Clinica Cerrada " + siguiente();

        crearClinica(token, abierta, depto, EN_SANTA_ANA);
        int idCerrada = crearClinica(token, cerrada, depto, EN_SANTA_ANA);

        mockMvc.perform(patch("/clinics/{id}/estado", idCerrada)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"INACTIVA\"}"))
                .andExpect(status().isOk());

        assertEquals(List.of(abierta), nombres(publicas(depto)));
    }

    @Test
    @DisplayName("una clinica sin coordenadas SI sale, con latitud y longitud en null")
    void incluyeLasQueNoTienenCoordenadas() throws Exception {
        // Existe y atiende; solo le falta que alguien le tome el GPS. Es la
        // pagina la que la lista aparte en vez de inventarle un punto.
        String depto = departamentoUnico();
        crearClinica(tokenDeMedico(), "Clinica Sin Gps " + siguiente(), depto, "");

        JsonNode lista = publicas(depto);
        assertEquals(1, lista.size());
        assertTrue(lista.get(0).get("latitud").isNull());
        assertTrue(lista.get(0).get("longitud").isNull());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 2 · lo que muestra el marcador, y nada mas
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("trae nombre, direccion, telefono y horario, y ningun dato interno")
    void exponeSoloLosCamposPublicos() throws Exception {
        String depto = departamentoUnico();
        String nombre = "Clinica Ficha " + siguiente();
        crearClinica(tokenDeMedico(), nombre, depto, EN_SANTA_ANA);

        JsonNode clinica = publicas(depto).get(0);

        assertEquals(nombre, clinica.get("name").asText());
        assertEquals("Calle El Progreso 12", clinica.get("direccion").asText());
        assertEquals("2440-5566", clinica.get("telefono").asText());
        assertEquals("Lunes a viernes, 7:00 a 16:00", clinica.get("horario").asText());
        assertEquals(13.9942, clinica.get("latitud").asDouble(), 1e-9);
        assertEquals(-89.5597, clinica.get("longitud").asDouble(), 1e-9);

        // Se compara el conjunto ENTERO de claves y no se busca una prohibida
        // en concreto: un campo nuevo que alguien agregue por descuido -el
        // dueño, el personal, el estado- hace caer la prueba aunque nadie haya
        // pensado en el.
        Set<String> claves = new TreeSet<>();
        for (Iterator<String> it = clinica.fieldNames(); it.hasNext(); ) {
            claves.add(it.next());
        }
        assertEquals(new TreeSet<>(CAMPOS_PUBLICOS), claves);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 3 · filtro por departamento
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("filtrar por departamento deja solo las de ese departamento")
    void filtraPorDepartamento() throws Exception {
        String token = tokenDeMedico();
        String uno = departamentoUnico();
        String otro = departamentoUnico();
        String deUno = "Clinica Del Uno " + siguiente();
        String deOtro = "Clinica Del Otro " + siguiente();

        crearClinica(token, deUno, uno, EN_SANTA_ANA);
        crearClinica(token, deOtro, otro, EN_SANTA_ANA);

        assertEquals(List.of(deUno), nombres(publicas(uno)));
        assertEquals(List.of(deOtro), nombres(publicas(otro)));
    }

    @Test
    @DisplayName("el filtro ignora tildes y mayusculas: el departamento es texto libre")
    void elFiltroIgnoraTildesYMayusculas() throws Exception {
        int n = siguiente();
        String nombre = "Clinica Tildes " + n;
        crearClinica(tokenDeMedico(), nombre, "Usulután " + n, EN_SANTA_ANA);

        assertEquals(List.of(nombre), nombres(publicas("usulutan " + n)));
        assertEquals(List.of(nombre), nombres(publicas("  USULUTÁN " + n + "  ")));
    }

    @Test
    @DisplayName("un departamento en blanco es lo mismo que no filtrar")
    void departamentoEnBlancoNoFiltra() throws Exception {
        crearClinica(tokenDeMedico(), "Clinica Blanco " + siguiente(), departamentoUnico(), EN_SANTA_ANA);

        assertFalse(publicas("").isEmpty());
        assertFalse(publicas("   ").isEmpty());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 4 · 50 clinicas en menos de 3 segundos
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("con 50 clinicas responde muy por debajo del presupuesto de 3 segundos")
    void cincuentaClinicasRespondenRapido() throws Exception {
        // Los 3 segundos del criterio son de la pagina entera: la peticion, el
        // JavaScript del mapa y las teselas. Al backend le toca una parte
        // pequena de ese presupuesto, por eso se le exige un segundo y no tres:
        // si esta peticion sola se comiera los tres, el criterio ya estaria
        // perdido antes de dibujar nada.
        String token = tokenDeMedico();
        String depto = departamentoUnico();
        for (int i = 0; i < 50; i++) {
            crearClinica(token, "Clinica Carga " + siguiente(), depto, EN_SANTA_ANA);
        }

        // Una peticion de calentamiento: la primera de la JVM paga la
        // compilacion de la consulta y del serializador, y eso no es lo que
        // ve un paciente en un servidor que ya lleva rato arriba.
        publicas(depto);

        long inicio = System.nanoTime();
        JsonNode filtradas = publicas(depto);
        long filtradoMs = (System.nanoTime() - inicio) / 1_000_000;

        inicio = System.nanoTime();
        mockMvc.perform(get("/clinics/publicas")).andExpect(status().isOk());
        long todasMs = (System.nanoTime() - inicio) / 1_000_000;

        assertEquals(50, filtradas.size());
        assertTrue(filtradoMs < 1000, "el filtro tardo " + filtradoMs + " ms");
        assertTrue(todasMs < 1000, "el catalogo completo tardo " + todasMs + " ms");
    }

    // ══════════════════════════════════════════════════════════════════════
    // Documentacion (TT-03): Swagger dice que es publico
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Swagger lo documenta como publico y sin candado de JWT")
    void swaggerLoDocumentaComoPublico() throws Exception {
        String cuerpo = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode operacion = json.readTree(cuerpo).path("paths").path("/clinics/publicas").path("get");

        String descripcion = operacion.path("description").asText();
        assertTrue(descripcion.contains("público, sin token"), descripcion);

        JsonNode seguridad = operacion.path("security");
        assertTrue(seguridad.isMissingNode() || !seguridad.toString().contains("jwt"), seguridad.toString());
    }
}
