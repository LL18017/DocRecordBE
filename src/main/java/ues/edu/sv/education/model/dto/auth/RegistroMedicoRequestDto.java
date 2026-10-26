package ues.edu.sv.education.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import ues.edu.sv.education.model.dto.auth.ReglaDeContrasena;

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
        @Pattern(regexp = ReglaDeContrasena.PATRON, message = ReglaDeContrasena.MENSAJE)
        String password,

        @Schema(description = "ID de la especialidad medica", example = "1")
        @NotNull(message = "La especialidad es obligatoria")
        Long especialidadId,

        // Obligatorio (HU-02 criterio 1): es lo que distingue a un médico
        // habilitado de cualquiera que llene el formulario. El formato es
        // básico a propósito -- 3 a 20 letras, números o guiones, con al menos
        // un número--: la Junta no publica un patrón único (JVPM-1234, 12345,
        // JVPM-ENF-4471 conviven) y rechazar uno legítimo es peor que dejar
        // pasar uno raro. Se guarda en mayúsculas y sin espacios alrededor.
        @Schema(description = "Número de registro en la Junta de Vigilancia de la Profesión Médica", example = "JVPM-12345")
        @NotBlank(message = "El número de junta de vigilancia es obligatorio")
        @Pattern(regexp = "^(?=.*\\d)[A-Za-z0-9-]{3,20}$",
                message = "El número de junta de vigilancia no tiene un formato válido: use de 3 a 20 letras, números o guiones, con al menos un número")
        String registroJunta

        // Sin `roles`: /auth/register es autogestion publica y siempre asigna
        // MEDICO. Ver AuthService.registrarMedico.

) {
}
