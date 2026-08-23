package ues.edu.sv.education.model.dto.prescripcion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Lo que se pide para emitir una receta.
 *
 * NO lleva medicoId: quien firma es el medico autenticado, tomado del token.
 * La firma de una receta no es un dato que se elija en un formulario.
 *
 * La lista de medicamentos es @NotEmpty: una receta sin medicamentos no es una
 * receta, es un papel firmado. Ademas de esta validacion, el servicio vuelve a
 * comprobarlo (ver PrescripcionService), porque la regla es del dominio y no
 * del formulario.
 */
@Schema(description = "Datos para emitir una receta")
public record PrescripcionRequestDto(

        @Schema(description = "Consulta de la que sale la receta", example = "7")
        @NotNull(message = "La consulta es obligatoria")
        Long consultaId,

        @Schema(description = "Medicamentos indicados. No puede ir vacia")
        @NotEmpty(message = "Una receta debe llevar al menos un medicamento")
        @Valid
        List<MedicamentoRequestDto> medicamentos

) {
}
