package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TT-03 · DRS-104: la documentacion de la API con OpenAPI.
 *
 *   criterio 2. cada endpoint protegido dice que rol pide y se puede probar con
 *               un token JWT.
 *   criterio 4. en produccion Swagger no queda expuesto sin autenticacion.
 *
 * Los criterios 1 y 3 (endpoints agrupados por modulo, documentacion generada
 * desde el codigo) los da springdoc por construccion.
 */
class DocumentacionDeLaApiIT extends PruebaClinica {

    private JsonNode documentacion() throws Exception {
        String cuerpo = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private String descripcion(JsonNode doc, String ruta, String verbo) {
        return doc.path("paths").path(ruta).path(verbo).path("description").asText();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 2: el rol que pide cada endpoint
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("el permiso del metodo aparece en la descripcion, y manda sobre el de la clase")
    void elPermisoDelMetodoApareceEnLaDescripcion() throws Exception {
        JsonNode doc = documentacion();

        String registrar = descripcion(doc, "/alergias", "post");
        assertTrue(registrar.contains("**Acceso:** MEDICO, ENFERMERA."), registrar);

        // GET /alergias/{id} no tiene permiso propio: hereda el de la clase.
        String leer = descripcion(doc, "/alergias/{id}", "get");
        assertTrue(leer.contains("**Acceso:** ADMIN, MEDICO, ENFERMERA."), leer);
    }

    @Test
    @DisplayName("los endpoints publicos dicen que no piden token")
    void losPublicosDicenQueNoPidenToken() throws Exception {
        JsonNode doc = documentacion();

        String login = descripcion(doc, "/auth/login", "post");
        assertTrue(login.contains("público, sin token"), login);

        String especialidades = descripcion(doc, "/especialidades", "get");
        assertTrue(especialidades.contains("público, sin token"), especialidades);
    }

    @Test
    @DisplayName("un endpoint protegido lleva el candado de JWT y uno publico no")
    void losProtegidosLlevanElCandadoDeJwt() throws Exception {
        JsonNode doc = documentacion();

        JsonNode pacientes = doc.path("paths").path("/pacientes").path("get").path("security");
        assertTrue(pacientes.toString().contains("jwt"), pacientes.toString());

        JsonNode login = doc.path("paths").path("/auth/login").path("post").path("security");
        assertTrue(login.isMissingNode() || !login.toString().contains("jwt"), login.toString());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 4: en produccion Swagger no queda expuesto
    // ══════════════════════════════════════════════════════════════════════

    private static String basic(String usuario, String clave) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((usuario + ":" + clave).getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @TestPropertySource(properties = {
            "app.swagger.publico=false",
            "app.swagger.usuario=tutora",
            "app.swagger.clave=Clave-De-Prueba-2026"
    })
    @DisplayName("en produccion, con credenciales configuradas")
    class ConCredenciales {

        @Test
        @DisplayName("sin credenciales, ni la documentacion ni la interfaz responden")
        void sinCredencialesNoResponde() throws Exception {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("con la clave equivocada tampoco")
        void conLaClaveEquivocadaNoResponde() throws Exception {
            mockMvc.perform(get("/v3/api-docs")
                            .header("Authorization", basic("tutora", "otra-clave")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("el token de una cuenta del sistema no abre Swagger: son credenciales aparte")
        void unTokenDelSistemaNoAbreSwagger() throws Exception {
            mockMvc.perform(get("/v3/api-docs")
                            .header("Authorization", bearer(tokenDeAdministrador())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("con las credenciales de Swagger, responde")
        void conLasCredencialesResponde() throws Exception {
            mockMvc.perform(get("/v3/api-docs")
                            .header("Authorization", basic("tutora", "Clave-De-Prueba-2026")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("proteger Swagger no toca la API: el login sigue sin pedir nada")
        void laApiSigueIgual() throws Exception {
            mockMvc.perform(get("/especialidades")).andExpect(status().isOk());
        }
    }

    @Nested
    @TestPropertySource(properties = {
            "app.swagger.publico=false",
            "app.swagger.usuario=",
            "app.swagger.clave="
    })
    @DisplayName("en produccion, si nadie configuro las credenciales")
    class SinCredenciales {

        @Test
        @DisplayName("Swagger queda cerrado, no abierto")
        void quedaCerrado() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().is4xxClientError());
            mockMvc.perform(get("/v3/api-docs")
                            .header("Authorization", basic("cualquiera", "cualquiera")))
                    .andExpect(status().is4xxClientError());
        }
    }
}
