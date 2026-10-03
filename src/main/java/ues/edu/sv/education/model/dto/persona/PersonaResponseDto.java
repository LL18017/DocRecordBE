package ues.edu.sv.education.model.dto.persona;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Datos de una persona")
public record PersonaResponseDto(

        @Schema(description = "ID de la persona", example = "42")
        Long personaId,

        @Schema(description = "DUI, puede ser null si no se ha capturado", example = "04871239-5")
        String dui,

        @Schema(description = "Nombres", example = "María Elena")
        String nombres,

        @Schema(description = "Apellidos", example = "López Torres")
        String apellidos,

        @Schema(description = "Fecha de nacimiento, puede ser null", example = "1991-03-14")
        LocalDate fechaNacimiento,

        @Schema(description = "Sexo (M/F), puede ser null", example = "F")
        String sexo,

        @Schema(description = "Telefono, puede ser null")
        String telefono,

        @Schema(description = "Direccion, puede ser null")
        String direccion,

        @Schema(description = "Correo de contacto, puede ser null")
        String email

) {
}
