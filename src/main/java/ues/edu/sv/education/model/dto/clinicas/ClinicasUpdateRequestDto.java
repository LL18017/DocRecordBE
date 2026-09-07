package ues.edu.sv.education.model.dto.clinicas;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos requeridos para actualizar una clínica")
public record ClinicasUpdateRequestDto(

        @Schema(
                description = "Nombre de la clínica",
                example = "Clínica Regional de Santa Ana"
        )
        @Size(max = 100, message = "El nombre de la clínica no puede superar los 100 caracteres")
        String name,

        @Schema(
                description = "Latitud de la clínica",
                example = "13.9942"
        )
        Double latitud,

        @Schema(
                description = "Longitud de la clínica",
                example = "-89.5597"
        )
        Double longitud


) {
}