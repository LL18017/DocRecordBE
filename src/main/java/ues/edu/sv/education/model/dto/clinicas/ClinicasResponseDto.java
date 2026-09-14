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
        Double longitud,

        @Schema(description = "Departamento", example = "Santa Ana", nullable = true)
        String departamento,

        @Schema(description = "Municipio", example = "Santa Ana", nullable = true)
        String municipio,

        @Schema(description = "Direccion exacta", nullable = true)
        String direccion,

        @Schema(description = "Telefono de contacto", nullable = true)
        String telefono,

        @Schema(description = "Horario de atencion", nullable = true)
        String horario,

        // Las cinco de arriba admiten null porque las clinicas registradas
        // antes de V16 no las tienen, y no se puede inventar la direccion de
        // una sede que ya existe. Las nuevas las exigen todas.

        @Schema(description = "ACTIVA o INACTIVA", example = "ACTIVA")
        String estado

) {
}
