package ues.edu.sv.education.model.dto.prescripcion;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Medicamento indicado dentro de una receta")
public record MedicamentoResponseDto(
        @Schema(example = "15") Long id,
        @Schema(example = "Amoxicilina 500 mg") String medicamento,
        @Schema(example = "1 tableta") String dosis,
        @Schema(example = "Cada 8 horas") String frecuencia,
        @Schema(example = "7 dias") String duracion
) {
}
