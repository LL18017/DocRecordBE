package ues.edu.sv.education.controller.error;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.AccessDeniedException;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Traduce cualquier excepcion que salga de un controller a una respuesta HTTP.
 *
 * ── El contrato (TT-01, criterio 1) ───────────────────────────────────────
 * Todo cuerpo de error de esta API trae las mismas cinco claves (ver
 * CuerpoDeError):
 *
 *     {"error": <categoria>, "message": <motivo concreto>,
 *      "codigo": <estado HTTP>, "timestamp": <ISO-8601>, "ruta": <URI>}
 *
 * `error` dice de que tipo de fallo se trata ("Registro no encontrado",
 * "Conflicto"); `message` dice que paso exactamente, redactado para que una
 * persona lo lea. No son sinonimos y no se pueden intercambiar: la interfaz
 * muestra `message` (ver `mensajeDirecto` en DocRecordFE/src/lib/api.ts, que
 * toma el primer valor no vacio entre `message`, `error` y `detail`), asi que
 * un cuerpo sin `message` deja al usuario con el texto generico por codigo
 * HTTP -"Los datos enviados no son validos"- en vez del motivo real.
 *
 * `codigo`, `timestamp` y `ruta` se agregaron DESPUES, sin tocar las dos
 * primeras, para que el cliente que ya leia `message` siga funcionando igual.
 * `codigo` repite el estado HTTP a proposito: criterio de TT-01, y asi el
 * cuerpo se entiende tambien guardado en un registro, sin la cabecera.
 *
 * El 500 agrega ademas `idError` (un UUID) que tambien queda en el registro del
 * servidor junto con la traza: es lo que permite rastrear el fallo que un
 * usuario reporta sin exponerle la traza (ver handleGenericException).
 *
 * Los dos manejadores de validacion mandan ADEMAS una entrada por campo
 * invalido, porque el par no puede decir QUE campo fallo (ver
 * respuestaDeValidacion, al final de la clase).
 *
 * El JwtFilter responde con este mismo formato por su cuenta, porque corre
 * antes del DispatcherServlet y ningun @ExceptionHandler lo alcanza.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /*
     * ============================================================
     * 404 - RECURSO NO ENCONTRADO
     * ============================================================
     *
     * Se utiliza cuando intentamos obtener un registro que
     * no existe en la base de datos.
     *
     * Ejemplo:
     *
     * GET /users/999
     *
     * Si el usuario 999 no existe y el servicio lanza:
     *
     * throw new EntityNotFoundException("Usuario no encontrado");
     *
     * se devuelve HTTP 404.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFoundException(
            EntityNotFoundException ex,
            HttpServletRequest request) {

        return respuesta(HttpStatus.NOT_FOUND, "Registro no encontrado", ex.getMessage(), request);
    }
    /*
     * ============================================================
     * 404 - RECURSO NO ENCONTRADO
     * ============================================================
     *
     * Se utiliza cuando intentamos obtener un registro que
     * no existe en la base de datos.
     *
     * Ejemplo:
     *
     * GET /users/999
     *
     * Si el usuario 999 no existe y el servicio lanza:
     *
     * throw new EntityNotFoundException("Usuario no encontrado");
     *
     * se devuelve HTTP 404.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFoundException(
            NoResourceFoundException ex,
            HttpServletRequest request) {

        return respuesta(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), request);
    }

    /*
     * ============================================================
     * 404 - RUTA SIN CONTROLLER (Spring, no la excepcion propia)
     * ============================================================
     *
     * Desde Spring Framework 6.1 una URL sin handler ya no dispara
     * NoHandlerFoundException (el handler de mas abajo): el propio
     * framework lanza org.springframework.web.servlet.resource.
     * NoResourceFoundException. Sin este handler cae en el generico de
     * Exception.class y una URL mal escrita responde 500 en vez de 404.
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSpringNoResourceFoundException(
            org.springframework.web.servlet.resource.NoResourceFoundException ex,
            HttpServletRequest request) {

        return respuesta(HttpStatus.NOT_FOUND, "Endpoint no encontrado", "La URL solicitada no existe", request);
    }

    /*
     * ============================================================
     * CODIGO VARIABLE - GeneralException
     * ============================================================
     *
     * A diferencia de NoResourceFoundException (siempre 404),
     * GeneralException carga su propio codigo HTTP como String (403,
     * 409, 422, ...) porque se usa para varias reglas de negocio
     * distintas -propiedad, conflicto, validacion cruzada de tablas-.
     * Sin este handler caia en el generico de Exception.class y
     * cualquier uso de GeneralException respondia 500 sin importar el
     * codigo que llevara.
     */
    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(
            GeneralException ex,
            HttpServletRequest request) {

        HttpStatus status;
        try {
            status = HttpStatus.valueOf(Integer.parseInt(ex.getErrorCode()));
        } catch (IllegalArgumentException e) {
            status = HttpStatus.BAD_REQUEST;
        }

        return respuesta(status, "Error", ex.getMessage(), request);
    }


    /*
     * ============================================================
     * 409 - CONFLICTO DE ESTADO
     * ============================================================
     *
     * Se utiliza cuando el estado actual del sistema impide
     * realizar una operación.
     *
     * Ejemplo:
     *
     * Intentar eliminar un usuario que tiene información
     * relacionada que impide su eliminación.
     *
     * NOTA:
     * Antes tenías NOT_FOUND (404) aquí.
     * Para un conflicto es más correcto utilizar 409.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(
            IllegalStateException ex,
            HttpServletRequest request) {

        return respuesta(HttpStatus.CONFLICT, "Conflicto", ex.getMessage(), request);
    }


    /*
     * ============================================================
     * 400 - ERROR DE VALIDACIÓN DEL REQUEST BODY
     * ============================================================
     *
     * Captura errores producidos por:
     *
     * @Valid
     * @NotBlank
     * @NotNull
     * @Email
     * @Size
     * @Min
     * @Max
     * etc.
     *
     * Ejemplo:
     *
     * @PostMapping
     * public User create(
     *     @Valid @RequestBody UserRequestDto request
     * )
     *
     * Si el email está vacío, se devuelve:
     *
     * {
     *     "error": "Datos inválidos",
     *     "message": "El correo no puede estar vacío.",
     *     "email": "El correo no puede estar vacío"
     * }
     *
     * El par {error, message} lo añade respuestaDeValidacion; ahí está por qué.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        // LinkedHashMap y no HashMap: de este orden sale el de `message`, y con
        // un HashMap el mismo cuerpo inválido producía un texto distinto en cada
        // arranque, porque el orden de iteración depende del hash de los nombres.
        Map<String, String> errores = new LinkedHashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errores.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        return respuestaDeValidacion(errores, request);
    }


    /*
     * ============================================================
     * 400 - ERROR DE VALIDACIÓN DE PARÁMETROS
     * ============================================================
     *
     * Se utiliza principalmente para validaciones realizadas
     * directamente sobre parámetros del Controller.
     *
     * Ejemplo:
     *
     * @GetMapping("/{id}")
     * public User getUser(
     *     @PathVariable
     *     @Min(1)
     *     Integer id
     * )
     *
     * Puede producir ConstraintViolationException.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolationException(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        // Mismo motivo que en el manejador de arriba para no usar HashMap.
        Map<String, String> errores = new LinkedHashMap<>();

        ex.getConstraintViolations()
                .forEach(violation ->
                        errores.put(
                                violation.getPropertyPath().toString(),
                                violation.getMessage()
                        )
                );

        return respuestaDeValidacion(errores, request);
    }


    /*
     * ============================================================
     * 409 - ERROR DE INTEGRIDAD DE BASE DE DATOS
     * ============================================================
     *
     * Captura errores como:
     *
     * - Email duplicado
     * - Username duplicado
     * - Violación de UNIQUE
     * - Violación de FOREIGN KEY
     * - Violación de NOT NULL de la BD
     *
     * Ejemplo:
     *
     * @Column(unique = true)
     * private String email;
     *
     * Si intentamos registrar:
     *
     * mario992lopez@gmail.com
     *
     * y ya existe, PostgreSQL genera:
     *
     * duplicate key value violates unique constraint
     *
     * Spring normalmente lo envuelve en:
     *
     * DataIntegrityViolationException
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        String message = ex.getMostSpecificCause().getMessage();

        /*
         * Detectamos específicamente el caso de email duplicado.
         */
        if (message != null && message.contains("(email)")) {

            return respuesta(HttpStatus.CONFLICT, "Correo duplicado", "El correo electrónico ya está registrado", request);
        }

        /*
         * Cualquier otra violación de integridad.
         *
         * No devolvemos el mensaje completo de PostgreSQL porque
         * podría revelar información interna de la base de datos.
         */
        return respuesta(HttpStatus.CONFLICT, "Violación de integridad", "La operación no puede realizarse porque los datos entran en conflicto con información existente", request);
    }


    /*
     * ============================================================
     * 400 - JSON MAL FORMADO
     * ============================================================
     *
     * Captura errores cuando el cliente envía JSON inválido.
     *
     * Ejemplo:
     *
     * {
     *     "email": "mario@gmail.com",
     *     "userName": "mario"
     *     "password": "123"
     * }
     *
     * Falta una coma después de "mario".
     *
     * También puede ocurrir cuando se envía un tipo incorrecto:
     *
     * "userType": "ABC"
     *
     * cuando se espera Integer.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        return respuesta(HttpStatus.BAD_REQUEST, "Solicitud inválida", "El cuerpo de la solicitud no tiene un formato válido", request);
    }


    /*
     * ============================================================
     * 405 - MÉTODO HTTP NO PERMITIDO
     * ============================================================
     *
     * Ejemplo:
     *
     * El endpoint solamente permite:
     *
     * POST /users
     *
     * pero el cliente realiza:
     *
     * GET /users
     *
     * Entonces se devuelve 405.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {

        return respuesta(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP no permitido", "El método HTTP utilizado no está permitido para este endpoint", request);
    }


    /*
     * ============================================================
     * 404 - ENDPOINT NO EXISTE
     * ============================================================
     *
     * Captura solicitudes realizadas hacia una URL que no
     * corresponde a ningún endpoint de nuestra aplicación.
     *
     * Ejemplo:
     *
     * GET /usuarios/123/abc
     *
     * cuando esa ruta no existe.
     *
     * NOTA:
     * Para que Spring lance NoHandlerFoundException en ciertas
     * configuraciones puede ser necesario habilitar:
     *
     * spring.mvc.throw-exception-if-no-handler-found=true
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(
            NoHandlerFoundException ex,
            HttpServletRequest request) {

        return respuesta(HttpStatus.NOT_FOUND, "Endpoint no encontrado", "La URL solicitada no existe", request);
    }


    /*
     * ============================================================
     * 401 - ERROR DE AUTENTICACIÓN
     * ============================================================
     *
     * Lo lanza PasswordAuthProvider: credenciales que no cuadran, cuenta sin
     * confirmar, bloqueada o expirada.
     *
     * ── Por qué ya no responde ErrorResponseDTO ──────────────────────────
     * Ese record serializaba {"error": <motivo>, "code": 401} y era la SEGUNDA
     * forma de error de la API, contra el criterio 1 de TT-01: los otros once
     * manejadores de esta clase -y el JwtFilter- responden
     * {"error": <categoría>, "message": <motivo>}.
     *
     * Se conservó esa y no esta por dos razones:
     *
     *   1. Es la que ya usaba casi toda la clase, así que unificar se reduce a
     *      cambiar este manejador en vez de once.
     *
     *   2. El cliente lee `message` PRIMERO: `mensajeDirecto`, en
     *      DocRecordFE/src/lib/api.ts, se queda con el primer valor no vacío
     *      entre `message`, `error` y `detail`. Con {error, code} el motivo
     *      viajaba en `error` y se mostraba de rebote; si la unificación
     *      hubiera ido al revés y `message` desapareciera de la API, el usuario
     *      dejaría de leer "Credenciales incorrectas" y vería el texto genérico
     *      por código HTTP ("Tu sesión no es válida o expiró").
     *
     * ── Por qué el código ya no viaja en el cuerpo ───────────────────────
     * Iba duplicado: el mismo 401 está en el estado HTTP, que es de donde el
     * cliente lo lee de verdad (`ApiError.status`). Nadie consumía `code`; la
     * interfaz lo tiene incluso en su lista de claves que NO son un mensaje.
     *
     * La categoría se llama igual que la del JwtFilter -"No autenticado"- para
     * que un 401 se lea igual venga del filtro o del login. Es lo que hace que
     * el cuerpo de un correo inexistente y el de una contraseña equivocada
     * salgan idénticos (HU-01 criterio 2, ver SeguridadIT).
     */
    @ExceptionHandler(CustomAuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationError(
            CustomAuthenticationException ex,
            HttpServletRequest request) {

        // El código de la excepción es un int libre. Hoy todos los usos son
        // 401; si alguien la lanza con otro, la categoría dejaría de ser cierta
        // y se dice la genérica en vez de afirmar algo que la respuesta
        // contradice.
        String categoria = ex.getErrorCode() == HttpStatus.UNAUTHORIZED.value()
                ? "No autenticado"
                : "Error de autenticación";

        return ResponseEntity
                .status(ex.getErrorCode())
                .body(CuerpoDeError.de(ex.getErrorCode(), categoria, ex.getMessage(),
                        request.getRequestURI()));
    }


    /*
     * ============================================================
     * 403 - ACCESO DENEGADO
     * ============================================================
     *
     * El usuario está autenticado pero NO tiene permisos
     * suficientes para realizar la operación.
     *
     * Ejemplo:
     *
     * Usuario:
     *
     * ROLE_USER
     *
     * intenta acceder a:
     *
     * /admin/users
     *
     * que requiere:
     *
     * ROLE_ADMIN
     *
     * ── Por qué el cuerpo ya no lleva `usuario` ni `roles` ───────────────
     * Eran dos claves que solo aparecían en este 403, así que era el único
     * cuerpo de error de la API con cuatro campos en vez de dos: justo la
     * disparidad que TT-01 criterio 1 prohíbe. Y no se pierde nada, porque le
     * decían al cliente quién es y qué roles tiene -lo que él mismo acaba de
     * mandar dentro de su token-. Para diagnosticar sirve el registro del
     * servidor, no la respuesta.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(HttpServletRequest request) {

        return respuesta(HttpStatus.FORBIDDEN, "Acceso denegado", "No tienes permisos para realizar esta acción", request);
    }


    /*
     * ============================================================
     * 500 - ERROR INESPERADO
     * ============================================================
     *
     * Este es el "último recurso".
     *
     * Si ocurre una excepción que NO fue manejada por ninguno
     * de los handlers anteriores, llega aquí.
     *
     * MUY IMPORTANTE:
     *
     * NO devolvemos ex.getMessage() al cliente.
     *
     * El mensaje podría contener:
     *
     * - SQL
     * - nombres de tablas
     * - rutas internas
     * - información de Hibernate
     * - información sensible
     *
     * El detalle debe quedar únicamente en los logs del servidor.
     */
    /*
     * Servicio de IA (AIService, Ollama). Caido o lento: 503; rechazo la
     * solicitud: 502. Sin estos tres el error terminaria en el generico, 500.
     */
    @ExceptionHandler(TransientAiException.class)
    public ResponseEntity<Map<String, Object>> handleTransientAiException(
            TransientAiException ex,
            HttpServletRequest request) {
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible", "El servicio de inteligencia artificial no está disponible temporalmente.", request);
    }

    @ExceptionHandler(NonTransientAiException.class)
    public ResponseEntity<Map<String, Object>> handleNonTransientAiException(
            NonTransientAiException ex,
            HttpServletRequest request) {
        return respuesta(HttpStatus.BAD_GATEWAY, "Error del servicio de IA", "El servicio de inteligencia artificial rechazó la solicitud.", request);
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, Object>> handleResourceAccessException(
            ResourceAccessException ex,
            HttpServletRequest request) {
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible", "No se pudo conectar con el servicio de inteligencia artificial.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        // El detalle -con la traza- queda SOLO en el registro del servidor,
        // atado a un identificador que tambien se le devuelve al usuario: asi
        // quien reporta "me salio un error" puede darlo y se encuentra su
        // traza exacta sin exponerla en la respuesta (TT-01).
        String idError = UUID.randomUUID().toString();
        LOG.error("Error interno [idError={}] en {} {}", idError,
                request.getMethod(), request.getRequestURI(), ex);

        Map<String, Object> cuerpo = CuerpoDeError.de(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno",
                "Ocurrió un error inesperado en el servidor. Si el problema continúa, indique el código de error: "
                        + idError,
                request.getRequestURI());
        cuerpo.put("idError", idError);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(cuerpo);
    }


    /** Respuesta de error con el cuerpo uniforme de la API (ver CuerpoDeError). */
    private static ResponseEntity<Map<String, Object>> respuesta(
            HttpStatus estado, String error, String message, HttpServletRequest request) {

        return ResponseEntity
                .status(estado)
                .body(CuerpoDeError.de(estado, error, message, request.getRequestURI()));
    }


    /*
     * ============================================================
     * EL PAR {error, message} SOBRE UN CUERPO DE VALIDACIÓN
     * ============================================================
     *
     * Las validaciones son el único error que no cabe entero en el par: además
     * del motivo hay que decir QUÉ campo falló, y dos claves fijas no lo
     * expresan. En vez de elegir entre el contrato y el detalle se manda el par
     * ADEMÁS de una entrada por campo, así que el cuerpo sigue cumpliendo
     * TT-01 -todo error trae `error` y `message`- sin perder nada.
     *
     * `message` se arma aquí y no lo reconstruye el cliente. Antes el cuerpo
     * eran SOLO los campos, y la interfaz los concatenaba ella misma
     * (`mensajesDeValidacion` en DocRecordFE/src/lib/api.ts) para tener algo
     * que mostrar; era el único error de la API cuyo texto dependía de que
     * cada cliente supiera rehacerlo. Se juntan en el mismo orden en que los
     * devolvió el validador, cerrando cada uno con punto para que dos avisos
     * seguidos no se lean como una sola frase.
     *
     * El par se pone al FINAL a propósito: si algún día se valida un campo que
     * se llame `error` o `message`, quien gana es el contrato. Al revés, el
     * cuerpo dejaría de cumplirlo sin que nadie se enterara.
     */
    private static ResponseEntity<Map<String, Object>> respuestaDeValidacion(
            Map<String, String> porCampo, HttpServletRequest request) {

        String motivo = porCampo.values().stream()
                .filter(mensaje -> mensaje != null && !mensaje.isBlank())
                .map(mensaje -> mensaje.matches(".*[.!?]") ? mensaje : mensaje + ".")
                .collect(Collectors.joining(" "));

        Map<String, Object> cuerpo = new LinkedHashMap<>(porCampo);
        cuerpo.put("error", "Datos inválidos");
        cuerpo.put("message", motivo.isBlank()
                // Puede no quedar ningún mensaje aprovechable: una restricción
                // declarada sobre la clase entera no cuelga de ningún campo.
                // Sin esto el cuerpo saldría con `message` vacío, que para la
                // interfaz es lo mismo que no mandarlo.
                ? "Los datos enviados no son válidos"
                : motivo);
        CuerpoDeError.completar(cuerpo, HttpStatus.BAD_REQUEST.value(), request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(cuerpo);
    }
}