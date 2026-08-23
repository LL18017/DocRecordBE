package ues.edu.sv.education.model.dto.persona;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Datos de persona para crear o completar dentro de un alta de paciente")
public record PersonaRequestDto(

        @Schema(description = "Si viene, se completa esa persona existente en vez de crear una nueva", example = "42")
        Long personaId,

        @Schema(description = "DUI", example = "04871239-5")
        @Size(max = 10, message = "El DUI no puede superar los 10 caracteres")
        String dui,

        @Schema(description = "Nombres. Obligatorio si personaId no viene", example = "María Elena")
        @Size(max = 80, message = "Los nombres no pueden superar los 80 caracteres")
        String nombres,

        @Schema(description = "Apellidos. Obligatorio si personaId no viene", example = "López Torres")
        @Size(max = 80, message = "Los apellidos no pueden superar los 80 caracteres")
        String apellidos,

        @Schema(description = "Fecha de nacimiento", example = "1991-03-14")
        LocalDate fechaNacimiento,

        @Schema(description = "Sexo: M o F", example = "F")
        @Pattern(regexp = "[MF]", message = "El sexo debe ser M o F")
        String sexo,

        @Schema(description = "Telefono")
        @Size(max = 20, message = "El telefono no puede superar los 20 caracteres")
        String telefono,

        @Schema(description = "Direccion")
        @Size(max = 255, message = "La direccion no puede superar los 255 caracteres")
        String direccion

) {
}
