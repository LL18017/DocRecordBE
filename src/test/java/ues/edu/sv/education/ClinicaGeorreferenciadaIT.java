package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-26 (DRS-28) · Registro de clínica con georreferenciación.
 *
 * La historia trae una nota de diseño que vale la pena leer antes que el
 * código:
 *
 *   "la validación del criterio 2 evita el error clásico de invertir latitud y
 *    longitud, que coloca la clínica en medio del océano Índico sin que nadie
 *    lo note hasta la demostración"
 *
 * Ese es el caso que mide `invertirLatitudYLongitudSeRechaza`: no una
 * coordenada absurda, sino dos coordenadas correctas puestas al revés, que es
 * como ocurre de verdad.
 */
class ClinicaGeorreferenciadaIT extends PruebaClinica {

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    /** Cuerpo completo, con todo lo que el criterio 1 exige. */
    private String cuerpoDe(String nombre, String municipio, String coordenadas) {
        return """
                {"name":"%s","departamento":"Santa Ana","municipio":"%s",
                 "direccion":"Avenida Independencia Sur, Barrio Santa Bárbara",
                 "telefono":"2440-1234","horario":"Lunes a viernes, 7:00 a 16:00"%s}
                """.formatted(nombre, municipio, coordenadas);
    }

    private String nombreUnico() {
        return "Clinica De Prueba " + CONTADOR.incrementAndGet();
    }

    private JsonNode crear(String token, String cuerpo) throws Exception {
        String respuesta = mockMvc.perform(post("/clinics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 1 · el alta exige la dirección completa
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("el alta guarda departamento, municipio, dirección, teléfono y horario")
    void guardaLaDireccionCompleta() throws Exception {
        String token = tokenDeMedico();

        JsonNode creada = crear(token,
                cuerpoDe(nombreUnico(), "Santa Ana", ",\"latitud\":13.9942,\"longitud\":-89.5597"));

        assertEquals("Santa Ana", creada.get("departamento").asText());
        assertEquals("Santa Ana", creada.get("municipio").asText());
        assertEquals("2440-1234", creada.get("telefono").asText());
        assertTrue(creada.get("direccion").asText().contains("Independencia"));
        assertTrue(creada.get("horario").asText().contains("7:00"));
        // El criterio 5 pide que aparezca como ACTIVA.
        assertEquals("ACTIVA", creada.get("estado").asText());
    }

    @Test
    @DisplayName("sin municipio el alta se rechaza: no es un dato opcional")
    void elMunicipioEsObligatorio() throws Exception {
        // Un paciente que busca dónde atenderse necesita saber si le queda
        // cerca. Sin municipio, la clínica no puede aparecer en el directorio.
        mockMvc.perform(post("/clinics")
                        .header("Authorization", "Bearer " + tokenDeMedico())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Clinica Sin Municipio","departamento":"Santa Ana",
                                 "direccion":"Alguna calle","telefono":"2440-0000",
                                 "horario":"8 a 16"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 2 · las coordenadas caen dentro de El Salvador
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("invertir latitud y longitud se rechaza, en vez de guardar un punto en otro continente")
    void invertirLatitudYLongitudSeRechaza() throws Exception {
        // El caso real: las dos coordenadas son correctas, pero puestas al
        // revés. Con el rango del planeta entero (-90..90 / -180..180) esto
        // pasaba la validación y dejaba la clínica en el océano Índico.
        String token = tokenDeMedico();

        mockMvc.perform(post("/clinics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDe(nombreUnico(), "Santa Ana",
                                ",\"latitud\":-89.5597,\"longitud\":13.9942")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("una coordenada de otro país se rechaza aunque sea válida en el planeta")
    void fueraDelTerritorioSeRechaza() throws Exception {
        String token = tokenDeMedico();

        // Ciudad de México: latitud y longitud perfectamente válidas, y a
        // 1.200 km de la red nacional.
        mockMvc.perform(post("/clinics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDe(nombreUnico(), "Santa Ana",
                                ",\"latitud\":19.4326,\"longitud\":-99.1332")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("una clínica sin coordenadas sigue pudiendo registrarse")
    void lasCoordenadasSiguenSiendoOpcionales() throws Exception {
        // Se da de alta mucho antes de que alguien vaya a tomarle el GPS.
        // Exigirlas sólo consigue que alguien escriba una cualquiera, y una
        // coordenada falsa es peor que una ausente.
        JsonNode creada = crear(tokenDeMedico(), cuerpoDe(nombreUnico(), "Santa Ana", ""));

        assertTrue(creada.get("latitud").isNull());
        assertTrue(creada.get("longitud").isNull());
        assertEquals("ACTIVA", creada.get("estado").asText());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 4 · no dos con el mismo nombre en el mismo municipio
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("el mismo nombre en el mismo municipio se rechaza con 409")
    void nombreRepetidoEnElMismoMunicipio() throws Exception {
        String token = tokenDeMedico();
        String nombre = nombreUnico();

        crear(token, cuerpoDe(nombre, "Santa Ana", ""));

        mockMvc.perform(post("/clinics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDe(nombre, "Santa Ana", "")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("y también cuando sólo cambian las tildes o las mayúsculas")
    void nombreRepetidoIgnorandoTildes() throws Exception {
        // «Clinica San Jose» y «Clínica San José» son el mismo sitio escrito
        // por dos personas distintas. Un UNIQUE literal las dejaría convivir.
        String token = tokenDeMedico();
        int n = CONTADOR.incrementAndGet();

        crear(token, cuerpoDe("Clinica San Jose " + n, "Sonsonate", ""));

        mockMvc.perform(post("/clinics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDe("CLÍNICA SAN JOSÉ " + n, "sonsonate", "")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("el mismo nombre en OTRO municipio sí se permite")
    void mismoNombreEnOtroMunicipio() throws Exception {
        // Una red nacional tiene «Clínica Central» en varias ciudades, y son
        // sedes distintas. El criterio acota el choque al municipio a propósito.
        String token = tokenDeMedico();
        String nombre = nombreUnico();

        crear(token, cuerpoDe(nombre, "Santa Ana", ""));
        JsonNode otra = crear(token, cuerpoDe(nombre, "San Miguel", ""));

        assertEquals("San Miguel", otra.get("municipio").asText());
    }

    // ══════════════════════════════════════════════════════════════════════
    // HU-27 · edición y baja
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("dar de baja una clínica cambia su estado y no la borra")
    void darDeBajaNoBorra() throws Exception {
        String token = tokenDeMedico();
        JsonNode creada = crear(token, cuerpoDe(nombreUnico(), "Santa Ana", ""));
        int id = creada.get("clinicaId").asInt();

        String cuerpo = mockMvc.perform(patch("/clinics/{id}/estado", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"INACTIVA\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals("INACTIVA", json.readTree(cuerpo).get("estado").asText());

        // Sigue existiendo, con su dirección intacta.
        String listado = mockMvc.perform(get("/clinics/mias")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        boolean aparece = false;
        for (JsonNode c : json.readTree(listado)) {
            if (c.get("clinicaId").asInt() == id) {
                aparece = true;
                assertEquals("INACTIVA", c.get("estado").asText());
                assertEquals("Santa Ana", c.get("municipio").asText());
            }
        }
        assertTrue(aparece, "dar de baja no debe hacer desaparecer la clínica");
    }

    @Test
    @DisplayName("un estado inexistente se rechaza con 400")
    void estadoInvalidoSeRechaza() throws Exception {
        String token = tokenDeMedico();
        int id = crear(token, cuerpoDe(nombreUnico(), "Santa Ana", "")).get("clinicaId").asInt();

        mockMvc.perform(patch("/clinics/{id}/estado", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN REMODELACION\"}"))
                .andExpect(status().isBadRequest());
    }
}
