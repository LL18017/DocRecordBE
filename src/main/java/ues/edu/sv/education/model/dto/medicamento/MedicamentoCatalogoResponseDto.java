package ues.edu.sv.education.model.dto.medicamento;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Medicamento del catalogo")
public record MedicamentoCatalogoResponseDto(
        @Schema(example = "6") Long medicamentoId,
        @Schema(example = "Amoxicilina") String nombreGenerico,
        @Schema(example = "Amoxil") String nombreComercial,
        @Schema(example = "Amoxicilina") String principioActivo,
        @Schema(example = "Capsula") String presentacion,
        @Schema(example = "500 mg") String concentracion,
        @Schema(description = "Solo los activos se pueden recetar") boolean activo,
        @Schema(description = "El producto en una linea; es lo que queda escrito en la receta",
                example = "Amoxicilina 500 mg (Amoxil), Capsula")
        String descripcion
) {
}
