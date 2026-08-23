package ues.edu.sv.education.model.dto.consulta;

import io.swagger.v3.oas.annotations.media.Schema;

/** Quien atendio, con su especialidad: es parte de leer una consulta. */
@Schema(description = "Identificacion del medico que atendio")
public record MedicoResumenDto(
        @Schema(example = "7") Long personaId,
        @Schema(example = "Carlos") String nombres,
        @Schema(example = "Menjívar") String apellidos,
        @Schema(example = "Medicina General") String especialidad
) {
}
