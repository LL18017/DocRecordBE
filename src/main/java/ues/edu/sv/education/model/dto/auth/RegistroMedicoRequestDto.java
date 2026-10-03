package ues.edu.sv.education.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Datos para la autogestion publica de una cuenta de medico")
public record RegistroMedicoRequestDto(

        @Schema(description = "Nombres de la persona", example = "Juan Armando")
        @NotBlank(message = "Los nombres no pueden estar vacios")
        @Size(max = 80, message = "Los nombres no pueden superar los 80 caracteres")
        String nombres,

        @Schema(description = "Apellidos de la persona", example = "Guerra Guevara")
        @NotBlank(message = "Los apellidos no pueden estar vacios")
        @Size(max = 80, message = "Los apellidos no pueden superar los 80 caracteres")
        String apellidos,

        @Schema(description = "Correo electronico", example = "juan.guerra@docrecord.sv")
        @Email(message = "Formato no valido para email")
        @NotBlank(message = "El correo no puede estar vacio")
        @Size(max = 100, message = "El email debe poseer menos de 100 caracteres")
        String email,

        @Schema(description = "Contrasena de la cuenta")
        @NotBlank(message = "La contrasena no puede estar vacia")
        String password,

        @Schema(description = "ID de la especialidad medica", example = "1")
        @NotNull(message = "La especialidad es obligatoria")
        Long especialidadId

        // Sin `roles`: /auth/register es autogestion publica y siempre asigna
        // MEDICO. Ver AuthService.registrarMedico.

) {
}
