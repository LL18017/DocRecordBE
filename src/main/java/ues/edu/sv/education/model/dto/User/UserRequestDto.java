package ues.edu.sv.education.model.dto.User;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.List;

@Schema(description = "Datos requeridos para registrar un nuevo usuario")
public record UserRequestDto(

        @Schema(description = "Correo electrónico del usuario", example = "mario992lopez@gmail.com")
        @Email(message = "Formato no válido para email")
        @Size(max = 100, message = "El email debe poseer menos de 100 caracteres")
        @NotBlank(message = "El correo no puede estar vacío")
        String email,

        @Schema(description = "Nombre de usuario", example = "jperez")
        @NotBlank(message = "El usuario no puede estar vacío")
        String userName,

        @Schema(description = "Contraseña del usuario", example = "12345")
        @NotBlank(message = "La contraseña no puede estar vacía")
        String password,

        @Schema(description = "Lista de IDs de roles asignados al usuario", example = "[1, 2]")
        @NotEmpty(message = "Debe seleccionar al menos un rol")
        List<Integer> roles,

        @Schema(description = "ID del tipo de usuario (ej. 1=DOCTOR, 2=ENFERMERA, 3=EMPLEADO)", example = "1")
        @NotNull(message = "El tipo de usuario es obligatorio")
        Integer userType
) {
}