package ues.edu.sv.education.model.dto.medicamento;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Activar o desactivar un medicamento del catalogo.
 *
 * Boolean y no boolean: con el primitivo, un cuerpo vacio llegaria como
 * `false` y desactivaria el producto sin que nadie lo pidiera.
 */
@Schema(description = "Nuevo estado del medicamento")
public record CambiarEstadoMedicamentoRequestDto(

        @Schema(description = "false lo saca de las recetas nuevas", example = "false")
        @NotNull(message = "Indica si el medicamento queda activo o no")
        Boolean activo
) {
}
