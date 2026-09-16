package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * TT-01, criterio 1: toda la API responde los errores con el MISMO cuerpo.
 *
 * ── Que habia antes ───────────────────────────────────────────────────────
 * Dos formas distintas conviviendo. Once manejadores del
 * GlobalExceptionHandler -y el JwtFilter- respondian
 * {"error": <categoria>, "message": <motivo>}; el de autenticacion respondia
 * un ErrorResponseDTO, o sea {"error": <motivo>, "code": 401}. En la segunda
 * la misma clave `error` significaba otra cosa y no habia `message`, que es
 * justo lo que la interfaz muestra.
 *
 * ── Por que una clase entera para esto ────────────────────────────────────
 * Porque el formato es un contrato de TODA la API, no de un endpoint: cada
 * prueba de las otras clases comprueba el codigo de estado de SU caso y
 * ninguna mira la forma del cuerpo, asi que la divergencia sobrevivio a 226
 * pruebas en verde. Lo que se prueba aqui es la propiedad comun, recorriendo
 * de una vez los seis caminos por los que la API contesta un error.
 */
class FormatoDeErroresIT extends PruebaClinica {

    private String token;

    @BeforeEach
    void autenticarUnMedico() throws Exception {
        token = tokenDeMedico();
    }

    /**
     * Los seis caminos por los que sale un error, cada uno por un manejador
     * distinto: validacion de @Valid, cuerpo ilegible, autenticacion fallida,
     * el filtro que corre antes del DispatcherServlet, autorizacion denegada,
     * URL inexistente, registro inexistente y metodo no permitido.
     *
     * Se declaran juntos a proposito: al anadir un manejador nuevo, la forma
     * de comprobar que cumple el contrato es agregar una linea aqui.
     */
    private Map<String, RequestBuilder> erroresDeCadaManejador() {
        Map<String, RequestBuilder> casos = new LinkedHashMap<>();

        casos.put("400 validacion: falta un campo obligatorio",
                post("/pacientes").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"));

        casos.put("400 cuerpo que no es JSON",
                post("/pacientes").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{esto no es json"));

        casos.put("401 credenciales incorrectas",
                post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nadie@ues.edu.sv\",\"password\":\"LoQueSea1!\"}"));

        casos.put("401 sin token (JwtFilter)", get("/pacientes"));

        casos.put("403 autenticado pero sin permiso",
                get("/user/all").header("Authorization", bearer(token))
                        .param("inicio", "0").param("fin", "50"));

        casos.put("404 URL que no existe",
                get("/esto-no-existe").header("Authorization", bearer(token)));

        casos.put("404 registro que no existe",
                get("/pacientes/99999999").header("Authorization", bearer(token)));

        casos.put("405 metodo no permitido", delete("/auth/login"));

        return casos;
    }

    @Test
    @DisplayName("todo cuerpo de error trae `error` y `message` (TT-01, criterio 1)")
    void todoCuerpoDeErrorTraeErrorYMessage() throws Exception {
        for (Map.Entry<String, RequestBuilder> caso : erroresDeCadaManejador().entrySet()) {
            String descripcion = caso.getKey();

            var respuesta = mockMvc.perform(caso.getValue()).andReturn().getResponse();

            assertTrue(respuesta.getStatus() >= 400,
                    descripcion + ": se esperaba un error y respondio " + respuesta.getStatus());
            assertTrue(respuesta.getContentType() != null
                            && respuesta.getContentType().contains(MediaType.APPLICATION_JSON_VALUE),
                    descripcion + ": el cuerpo no es JSON sino " + respuesta.getContentType());

            JsonNode cuerpo = json.readTree(respuesta.getContentAsString());

            assertTrue(cuerpo.hasNonNull("error"),
                    descripcion + ": falta la categoria `error`");
            assertFalse(cuerpo.get("error").asText().isBlank(),
                    descripcion + ": `error` viene vacia");

            // El que mas importa: es el que la interfaz pone en pantalla
            // (`mensajeDirecto` en DocRecordFE/src/lib/api.ts). Sin el, el
            // usuario lee el texto generico por codigo HTTP en vez del motivo.
            assertTrue(cuerpo.hasNonNull("message"),
                    descripcion + ": falta el motivo `message`");
            assertFalse(cuerpo.get("message").asText().isBlank(),
                    descripcion + ": `message` viene vacio");

            // El codigo ya viaja en el estado HTTP. Que vuelva al cuerpo
            // significa que alguien reintrodujo la forma {error, code}.
            assertFalse(cuerpo.has("code"),
                    descripcion + ": el codigo HTTP no debe duplicarse en el cuerpo");
        }
    }

    @Test
    @DisplayName("el 401 del login trae el motivo en `message`, no en `error`")
    void elLoginFallidoTraeElMotivoEnMessage() throws Exception {
        // Era el unico manejador con la otra forma: {"error":"Credenciales
        // incorrectas","code":401}. La interfaz lo mostraba de rebote, porque
        // `mensajeDirecto` cae a `error` cuando no hay `message`; en cuanto
        // alguien confiara en `message` -que es la clave que documenta el resto
        // de la API- el login habria dejado de explicar por que fallo.
        String cuerpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nadie@ues.edu.sv\",\"password\":\"LoQueSea1!\"}"))
                .andReturn().getResponse().getContentAsString();

        JsonNode error = json.readTree(cuerpo);

        assertEquals("Credenciales incorrectas", error.get("message").asText());
        // La misma categoria que usa el JwtFilter para sus 401, para que un
        // fallo de sesion se lea igual venga del filtro o del login.
        assertEquals("No autenticado", error.get("error").asText());
    }

    @Test
    @DisplayName("el 403 no le devuelve al cliente quien es ni que roles tiene")
    void elAccesoDenegadoNoRepiteLoQueElClienteYaSabe() throws Exception {
        // El cuerpo llevaba ademas `usuario` y `roles`, asi que era el unico
        // error de la API con cuatro claves. Ademas de romper el contrato, lo
        // que devolvia era lo que el propio cliente acababa de mandar en su
        // token.
        String cuerpo = mockMvc.perform(get("/user/all")
                        .header("Authorization", bearer(token))
                        .param("inicio", "0").param("fin", "50"))
                .andReturn().getResponse().getContentAsString();

        JsonNode error = json.readTree(cuerpo);

        assertEquals("Acceso denegado", error.get("error").asText());
        assertFalse(error.has("usuario"), "el 403 no debe devolver el usuario");
        assertFalse(error.has("roles"), "el 403 no debe devolver los roles");
    }

    @Test
    @DisplayName("una validacion cumple el contrato y ademas dice que campo fallo")
    void laValidacionTraeElParYTambienElCampo() throws Exception {
        // El par no puede expresar QUE campo fallo, asi que las validaciones
        // mandan las dos cosas. Si alguna vez se decide plegarlas al par a
        // secas, esta prueba obliga a que sea una decision y no un descuido:
        // la interfaz se quedaria sin poder senalar el campo en el formulario.
        String cuerpo = mockMvc.perform(post("/pacientes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getContentAsString();

        JsonNode error = json.readTree(cuerpo);

        assertEquals("Datos inválidos", error.get("error").asText());
        assertEquals("La persona es obligatoria", error.get("persona").asText());
        // `message` lo arma el servidor juntando los mensajes de los campos.
        // Antes el cuerpo eran solo los campos y cada cliente tenia que
        // concatenarlos por su cuenta para tener algo que mostrar.
        assertTrue(error.get("message").asText().contains("La persona es obligatoria"),
                "el motivo debe recoger lo que dijo la validacion");
    }
}
