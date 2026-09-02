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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException; // FIX: import faltante
import ues.edu.sv.education.model.dto.error.ErrorResponseDTO;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // FIX: Logger recomendado en vez de ex.printStackTrace() para producción.
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /*
     * ============================================================
     * 404 - RECURSO NO ENCONTRADO (ENTIDAD)
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
     * 404 - RECURSO ESTÁTICO NO ENCONTRADO
     * ============================================================
     *
     * Se lanza cuando Spring MVC no encuentra un recurso estático
     * (por ejemplo un archivo dentro de /static) o una ruta que
     * no coincide con ningún handler ni recurso servible.
     *
     * NOTA (FIX): faltaba el import de NoResourceFoundException,
     * por lo que esta clase NO compilaba.
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
     * @Valid @NotBlank @NotNull @Email @Size @Min @Max, etc.
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
     * Validaciones realizadas directamente sobre parámetros
     * del Controller (@PathVariable, @RequestParam con @Min, etc).
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
     * Captura errores como email/username duplicado o violación
     * de FOREIGN KEY / UNIQUE / NOT NULL.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {

        String message = ex.getMostSpecificCause().getMessage();

        if (message != null && message.contains("(email)")) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "Correo duplicado",
                            "message", "El correo electrónico ya está registrado"
                    ));
        }

        // No devolvemos el mensaje completo de PostgreSQL: podría
        // revelar información interna de la base de datos.
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
     * NOTA:
     * Para que Spring lance NoHandlerFoundException es necesario
     * configurar:
     * spring.mvc.throw-exception-if-no-handler-found=true
     * spring.web.resources.add-mappings=false
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
     * NOTA (FIX): faltaba el import de CustomAuthenticationException.
     * Ajusta el paquete al real de tu proyecto si es distinto de
     * ues.edu.sv.education.exception.CustomAuthenticationException
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
     * ERRORES RELACIONADOS AL SERVICIO DE IA (Spring AI / Ollama)
     * ============================================================
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponseDTO> handleIOException(IOException ex) {

        ErrorResponseDTO error = new ErrorResponseDTO(
                "No se pudo cargar la configuración del asistente.",
                500
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }

    @ExceptionHandler(TransientAiException.class)
    public ResponseEntity<ErrorResponseDTO> handleTransientAiException(
            TransientAiException ex) {

        ErrorResponseDTO error = new ErrorResponseDTO(
                "El servicio de inteligencia artificial no está disponible temporalmente.",
                503
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(error);
    }

    @ExceptionHandler(NonTransientAiException.class)
    public ResponseEntity<ErrorResponseDTO> handleNonTransientAiException(
            NonTransientAiException ex) {

        ErrorResponseDTO error = new ErrorResponseDTO(
                "El servicio de inteligencia artificial rechazó la solicitud.",
                502
        );

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(error);
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponseDTO> handleResourceAccessException(
            ResourceAccessException ex) {

        ErrorResponseDTO error = new ErrorResponseDTO(
                "No se pudo conectar con el servicio de inteligencia artificial.",
                503
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalArgumentException(
            IllegalArgumentException ex) {

        ErrorResponseDTO error = new ErrorResponseDTO(
                "Los datos enviados no son válidos.",
                400
        );

        return ResponseEntity.badRequest().body(error);
    }


    /*
     * ============================================================
     * 500 - ERROR INESPERADO (ÚLTIMO RECURSO)
     * ============================================================
     *
     * (handleGenericException y handleException). Eso es inválido:
     * Spring lanza en el arranque
     *   "Ambiguous @ExceptionHandler method mapped for [...]"
     * porque no puede decidir cuál usar para Exception.class.
     * Se dejó un único handler, unificado con ErrorResponseDTO
     * para mantener consistencia con los handlers de IA/IO.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericException(Exception ex) {

        // FIX: usar logger en vez de printStackTrace en producción.
        log.error("Error interno no controlado", ex);

        ErrorResponseDTO error = new ErrorResponseDTO(
                "Ocurrió un error inesperado en el servidor.",
                500
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }
}