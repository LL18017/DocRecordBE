package ues.edu.sv.education.controller.error;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import ues.edu.sv.education.model.dto.error.ErrorResponseDTO;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
    public ResponseEntity<Map<String, String>> handleEntityNotFoundException(
            EntityNotFoundException ex) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "error", "Registro no encontrado",
                        "message", ex.getMessage()
                ));
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
    public ResponseEntity<Map<String, String>> handleNoResourceFoundException(
            NoResourceFoundException ex) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "error", "Recurso no encontrado",
                        "message", ex.getMessage()
                ));
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
    public ResponseEntity<Map<String, String>> handleIllegalStateException(
            IllegalStateException ex) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "error", "Conflicto",
                        "message", ex.getMessage()
                ));
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
     *     "email": "El correo no puede estar vacío"
     * }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> errores = new HashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errores.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errores);
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
    public ResponseEntity<Map<String, String>> handleConstraintViolationException(
            ConstraintViolationException ex) {

        Map<String, String> errores = new HashMap<>();

        ex.getConstraintViolations()
                .forEach(violation ->
                        errores.put(
                                violation.getPropertyPath().toString(),
                                violation.getMessage()
                        )
                );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errores);
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
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {

        String message = ex.getMostSpecificCause().getMessage();

        /*
         * Detectamos específicamente el caso de email duplicado.
         */
        if (message != null && message.contains("(email)")) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "Correo duplicado",
                            "message", "El correo electrónico ya está registrado"
                    ));
        }

        /*
         * Cualquier otra violación de integridad.
         *
         * No devolvemos el mensaje completo de PostgreSQL porque
         * podría revelar información interna de la base de datos.
         */
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "error", "Violación de integridad",
                        "message", "La operación no puede realizarse porque los datos entran en conflicto con información existente"
                ));
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
    public ResponseEntity<Map<String, String>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "Solicitud inválida",
                        "message", "El cuerpo de la solicitud no tiene un formato válido"
                ));
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
    public ResponseEntity<Map<String, String>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Map.of(
                        "error", "Método HTTP no permitido",
                        "message", "El método HTTP utilizado no está permitido para este endpoint"
                ));
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
    public ResponseEntity<Map<String, String>> handleNoHandlerFound(
            NoHandlerFoundException ex) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "error", "Endpoint no encontrado",
                        "message", "La URL solicitada no existe"
                ));
    }


    /*
     * ============================================================
     * 401 - ERROR DE AUTENTICACIÓN
     * ============================================================
     *
     * Se utiliza cuando el usuario no está correctamente
     * autenticado.
     *
     * En tu proyecto tienes una excepción personalizada:
     *
     * CustomAuthenticationException
     *
     * Por lo tanto conservamos tu implementación.
     */
    @ExceptionHandler(CustomAuthenticationException.class)
    public ResponseEntity<ErrorResponseDTO> handleAuthenticationError(
            CustomAuthenticationException ex) {

        ErrorResponseDTO err =
                new ErrorResponseDTO(
                        ex.getMessage(),
                        ex.getErrorCode()
                );

        return ResponseEntity
                .status(ex.getErrorCode())
                .body(err);
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
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(
            HttpServletRequest request) {

        Authentication auth =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        String usuario =
                auth != null
                        ? auth.getName()
                        : "anónimo";

        String roles =
                auth != null
                        ? auth.getAuthorities().toString()
                        : "ninguno";

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "error", "Acceso denegado",
                        "message", "No tienes permisos para realizar esta acción",
                        "usuario", usuario,
                        "roles", roles
                ));
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
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(
            Exception ex) {

        /*
         * Esto aparecerá en los logs del servidor.
         *
         * En producción puedes utilizar Logger en lugar de
         * System.err.
         */
        ex.printStackTrace();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "Error interno",
                        "message", "Ocurrió un error inesperado en el servidor"
                ));
    }
}