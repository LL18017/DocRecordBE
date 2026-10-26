package ues.edu.sv.education;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ues.edu.sv.education.controller.error.GlobalExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
@Import(FormatoDeErroresIT.RutaQueFalla.class)
class FormatoDeErroresIT extends PruebaClinica {

    private String token;

    /**
     * Ruta que solo existe en las pruebas y revienta con una excepcion no
     * prevista, para llegar al manejador del 500: ninguna ruta real falla a
     * proposito. La excepcion lleva texto que NUNCA debe salir en la respuesta
     * (SQL, tabla, ruta del servidor).
     *
     * Se registra con @Import porque el escaneo de componentes de las pruebas
     * ignora las clases anidadas de una clase de prueba.
     */
    @RestController
    static class RutaQueFalla {
        static final String DETALLE_INTERNO =
                "ERROR: relation tabla_secreta does not exist en /srv/interno/app";

        @GetMapping("/prueba-error-500")
        String fallar() {
            throw new IllegalArgumentException(DETALLE_INTERNO);
        }
    }

    private final ListAppender<ILoggingEvent> registro = new ListAppender<>();
    private final Logger loggerDelManejador =
            (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @BeforeEach
    void capturarElRegistro() {
        registro.start();
        loggerDelManejador.addAppender(registro);
    }

    @AfterEach
    void soltarElRegistro() {
        loggerDelManejador.detachAppender(registro);
        registro.stop();
    }

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
    @DisplayName("todo cuerpo de error trae `error`, `message`, `codigo`, `timestamp` y `ruta` (TT-01, criterio 1)")
    void todoCuerpoDeErrorTraeErrorYMessage() throws Exception {
        for (Map.Entry<String, RequestBuilder> caso : erroresDeCadaManejador().entrySet()) {
            String descripcion = caso.getKey();

            var resultado = mockMvc.perform(caso.getValue()).andReturn();
            var respuesta = resultado.getResponse();

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

            // `codigo` repite el estado HTTP (criterio de TT-01). Lo que NO debe
            // volver es la forma vieja {error, code}: la clave en ingles.
            assertFalse(cuerpo.has("code"),
                    descripcion + ": la clave `code` es la forma vieja; la clave es `codigo`");
            assertTrue(cuerpo.get("codigo").isInt(),
                    descripcion + ": `codigo` debe ser numerico");
            assertEquals(respuesta.getStatus(), cuerpo.get("codigo").asInt(),
                    descripcion + ": `codigo` no coincide con el estado HTTP");

            assertTrue(cuerpo.hasNonNull("timestamp"),
                    descripcion + ": falta `timestamp`");
            // Instant.parse solo acepta ISO-8601: si el formato cambia, falla.
            Instant.parse(cuerpo.get("timestamp").asText());

            assertTrue(cuerpo.hasNonNull("ruta"),
                    descripcion + ": falta `ruta`");
            assertEquals(resultado.getRequest().getRequestURI(),
                    cuerpo.get("ruta").asText(),
                    descripcion + ": `ruta` no es la de la peticion");

            // Solo el 500 lleva identificador; en los demas seria ruido.
            assertFalse(cuerpo.has("idError"),
                    descripcion + ": solo el error 500 lleva `idError`");
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

    @Test
    @DisplayName("un error no controlado responde 500 sin traza ni detalle interno y con un idError")
    void unErrorNoControladoNoExponeNadaInterno() throws Exception {
        var respuesta = mockMvc.perform(get("/prueba-error-500")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse();

        assertEquals(500, respuesta.getStatus());
        String texto = respuesta.getContentAsString();
        JsonNode cuerpo = json.readTree(texto);

        assertEquals("Error interno", cuerpo.get("error").asText());
        assertEquals(500, cuerpo.get("codigo").asInt());
        assertEquals("/prueba-error-500", cuerpo.get("ruta").asText());
        Instant.parse(cuerpo.get("timestamp").asText());

        // Criterio 3: nada de la excepcion llega al cliente.
        assertFalse(texto.contains("tabla_secreta"), "el cuerpo expone el nombre de una tabla: " + texto);
        assertFalse(texto.contains("/srv/"), "el cuerpo expone una ruta interna: " + texto);
        assertFalse(texto.contains("IllegalArgumentException"), "el cuerpo expone la clase de la excepcion: " + texto);
        assertFalse(texto.contains("\tat ") || texto.contains("stackTrace") || texto.contains("trace"),
                "el cuerpo expone una traza: " + texto);

        // Criterio 4: el id se devuelve al usuario, en su propia clave y dentro
        // de `message` (que es lo unico que la interfaz muestra)...
        String idError = cuerpo.get("idError").asText();
        UUID.fromString(idError);
        assertTrue(cuerpo.get("message").asText().contains(idError),
                "el usuario no ve el id en `message`, que es lo que la interfaz muestra");

        // ...y el MISMO id esta en el registro del servidor, con la traza.
        var eventosConId = registro.list.stream()
                .filter(evento -> evento.getFormattedMessage().contains(idError))
                .toList();
        assertEquals(1, eventosConId.size(), "el registro debe tener exactamente una entrada con el idError");
        var evento = eventosConId.get(0);
        assertEquals(Level.ERROR, evento.getLevel());
        assertNotNull(evento.getThrowableProxy(), "el registro debe llevar la traza de la excepcion");
        assertTrue(evento.getThrowableProxy().getMessage().contains(RutaQueFalla.DETALLE_INTERNO),
                "la traza del registro debe conservar el detalle que se le oculta al cliente");
    }

    @Test
    @DisplayName("dos errores 500 reciben identificadores distintos")
    void cadaErrorInternoTieneSuPropioId() throws Exception {
        assertNotEquals(idDelError500(), idDelError500());
    }

    private String idDelError500() throws Exception {
        String cuerpo = mockMvc.perform(get("/prueba-error-500")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("idError").asText();
    }

    @Test
    @DisplayName("la validacion (400) lista cada campo rechazado y trae codigo, timestamp y ruta")
    void laValidacionTraeLosMetadatosComunes() throws Exception {
        var respuesta = mockMvc.perform(post("/pacientes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse();

        JsonNode error = json.readTree(respuesta.getContentAsString());

        assertEquals(400, respuesta.getStatus());
        assertEquals(400, error.get("codigo").asInt());
        assertEquals("/pacientes", error.get("ruta").asText());
        Instant.parse(error.get("timestamp").asText());
        // El campo rechazado y su motivo siguen en el cuerpo, junto a los metadatos.
        assertEquals("La persona es obligatoria", error.get("persona").asText());
    }
}
