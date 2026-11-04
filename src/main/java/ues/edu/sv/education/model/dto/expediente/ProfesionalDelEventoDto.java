package ues.edu.sv.education.model.dto.expediente;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Quien registro el evento: el medico de la consulta o de la receta, o la enfermera de la toma")
public record ProfesionalDelEventoDto(
        @Schema(example = "7") Long personaId,
        @Schema(example = "Carlos") String nombres,
        @Schema(example = "Menjívar") String apellidos,
        @Schema(example = "MEDICO", allowableValues = {"MEDICO", "ENFERMERA"}) String rol
) {
}
