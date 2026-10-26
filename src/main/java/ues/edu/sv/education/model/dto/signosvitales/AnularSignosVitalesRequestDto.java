package ues.edu.sv.education.model.dto.signosvitales;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la anulacion de una toma (HU-16 criterio 4).
 *
 * Solo el motivo: quien anula sale del token y la fecha la pone el servidor,
 * por lo mismo que `enfermeraId` no viaja al registrar una toma.
 */
@Schema(description = "Motivo por el que se anula una toma de constantes")
public record AnularSignosVitalesRequestDto(

        @Schema(description = "Por que la toma no vale", example = "Se tecleó el peso en la casilla de la talla")
        @NotBlank(message = "El motivo de la anulación es obligatorio")
        @Size(max = 500, message = "El motivo no puede pasar de 500 caracteres")
        String motivo

) {
}
