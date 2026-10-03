package ues.edu.sv.education.model.dto.paciente;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * El estado al que se quiere mover un paciente.
 *
 * Va en el cuerpo y no en la ruta (/pacientes/{id}/activar, /desactivar) para
 * que anadir un tercer estado no signifique anadir un endpoint: la operacion
 * es siempre la misma -- cambiar el estado -- y lo que varia es el valor.
 */
@Schema(description = "Estado al que se mueve el paciente")
public record CambiarEstadoRequestDto(

        @Schema(description = "ACTIVO o INACTIVO", example = "INACTIVO")
        @NotBlank(message = "El estado no puede estar vacio")
        String estado
) {
}
