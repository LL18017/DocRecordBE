package ues.edu.sv.education.model.dto.clinicas;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos de respuesta de una clínica")
public record ClinicasResponseDto(

        @Schema(
                description = "ID de la clínica",
                example = "1"
        )
        Integer clinicaId,

        @Schema(
                description = "Nombre de la clínica",
                example = "Clínica Regional de Santa Ana"
        )
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
