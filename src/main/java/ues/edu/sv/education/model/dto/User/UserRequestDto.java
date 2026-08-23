package ues.edu.sv.education.model.dto.User;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Datos requeridos para registrar un nuevo usuario")
public record UserRequestDto(

        @Schema(description = "Correo electrónico del usuario", example = "juan.perez@ues.edu.sv")
        @Email(message = "Formato no válido para email")
        @Size(max = 100, message = "El email debe poseer menos de 100 caracteres")
        @NotBlank(message = "El correo no puede estar vacío")
        String email,

        @Schema(description = "Nombre de usuario", example = "jperez")
        @NotBlank(message = "El usuario no puede estar vacío")
        String userName,

        @Schema(description = "Contraseña del usuario", example = "MiClave123!")
        @NotBlank(message = "La contraseña no puede estar vacía")
        String password,

        // No hay campo `roles`: el rol de un registro publico (/auth/register) lo
        // asigna el servidor, nunca el cliente. Ver AuthService.createUser.

        @Schema(description = "ID del tipo de usuario (ej. 1=DOCTOR, 2=ENFERMERA, 3=EMPLEADO)", example = "1")
        @NotNull(message = "El tipo de usuario es obligatorio")
        Integer userType
) {
}