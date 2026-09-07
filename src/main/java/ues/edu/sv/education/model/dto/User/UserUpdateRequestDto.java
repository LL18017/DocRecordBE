package ues.edu.sv.education.model.dto.User;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.List;

@Schema(description = "Datos requeridos para actualizar un  usuario")
public record UserUpdateRequestDto(

        @Schema(description = "Nombre de usuario", example = "jperez")
        String userName,

        @Schema(description = "Contraseña del usuario", example = "12345")
        String password,

        @Schema(description = "Lista de IDs de roles asignados al usuario", example = "[1, 2]")
        List<Integer> roles,

        @Schema(description = "ID del tipo de usuario (ej. 1=DOCTOR, 2=ENFERMERA, 3=EMPLEADO)", example = "1")
        Integer userType
) {
}