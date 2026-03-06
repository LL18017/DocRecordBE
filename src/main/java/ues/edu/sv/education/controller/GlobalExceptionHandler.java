package ues.edu.sv.education.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
}
