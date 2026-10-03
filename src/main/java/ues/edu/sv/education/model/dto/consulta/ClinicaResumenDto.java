package ues.edu.sv.education.model.dto.consulta;

import io.swagger.v3.oas.annotations.media.Schema;

/** Sucursal donde se atendio. Va null cuando la consulta no la registro. */
@Schema(description = "Sucursal donde se atendio la consulta")
public record ClinicaResumenDto(
        @Schema(example = "1") Integer clinicaId,
        @Schema(example = "Clínica Regional de Santa Ana") String name
) {
}
