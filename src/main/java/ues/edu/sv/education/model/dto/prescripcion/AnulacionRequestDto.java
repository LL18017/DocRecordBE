package ues.edu.sv.education.model.dto.prescripcion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lo que se pide para anular una receta: el motivo, que queda en el registro. */
@Schema(description = "Datos para anular una receta")
public record AnulacionRequestDto(

        @Schema(description = "Por que se anula (10 a 500 caracteres)", example = "Dosis mal capturada; se emite una nueva")
        @NotBlank(message = "El motivo de la anulación es obligatorio")
        @Size(min = 10, max = 500, message = "El motivo de la anulación debe tener entre 10 y 500 caracteres")
        String motivo

) {
}
