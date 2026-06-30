package ues.edu.sv.education.controller.error;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ues.edu.sv.education.model.dto.error.ErrorResponseDTO;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<String> handleEntityNotFoundException(EntityNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body("Registro no encontrado: " + ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalStateException(IllegalStateException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body("Conflicto: " + ex.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraintViolationException(
            ConstraintViolationException ex) {

        Map<String, String> errores = new HashMap<>();

        ex.getConstraintViolations().forEach(violation -> {
            errores.put(
                    violation.getPropertyPath().toString(),
                    violation.getMessage()
            );
        });

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errores);
    }

    @ExceptionHandler(CustomAuthenticationException.class)
    public ResponseEntity<ErrorResponseDTO> handleAuthenticationError(
            CustomAuthenticationException ex) {
        ErrorResponseDTO err = new ErrorResponseDTO(ex.getMessage(), ex.getErrorCode());
        return ResponseEntity.status( ex.getErrorCode()).body(err);
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        String usuario = auth != null ? auth.getName() : "anónimo";
        String roles   = auth != null ? auth.getAuthorities().toString() : "ninguno";



        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "error",   "No tienes permisos para esta acción",
                        "usuario", usuario,
                        "roles",   roles
                ));
    }
}
