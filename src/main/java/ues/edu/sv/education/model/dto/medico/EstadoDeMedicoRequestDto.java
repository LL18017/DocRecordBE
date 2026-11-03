package ues.edu.sv.education.model.dto.medico;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Dar de baja o reactivar la ficha del medico")
public record EstadoDeMedicoRequestDto(

        @Schema(example = "false")
        @NotNull(message = "Hay que indicar si la ficha queda activa")
        Boolean activo

) {
}
