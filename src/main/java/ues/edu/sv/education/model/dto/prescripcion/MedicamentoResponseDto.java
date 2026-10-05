package ues.edu.sv.education.model.dto.prescripcion;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Un renglon de una receta emitida.
 *
 * `medicamento` es el nombre tal como quedo en la receta, no el que tenga hoy
 * el catalogo. `medicamentoId` es null en las recetas anteriores al catalogo
 * (HU-23), que se escribieron como texto libre.
 */
@Schema(description = "Medicamento indicado dentro de una receta")
public record MedicamentoResponseDto(
        @Schema(example = "15") Long id,
        @Schema(description = "Id en el catalogo; null en recetas anteriores al catalogo", example = "6")
        Long medicamentoId,
        @Schema(example = "Amoxicilina 500 mg (Amoxil), Capsula") String medicamento,
        @Schema(example = "1 tableta") String dosis,
        @Schema(example = "Cada 8 horas") String frecuencia,
        @Schema(example = "7 dias") String duracion
) {
}
