package ues.edu.sv.education.model.dto.clinicas;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** El estado al que se mueve la clinica: ACTIVA o INACTIVA. */
@Schema(description = "Nuevo estado de la clinica")
public record CambiarEstadoClinicaRequestDto(

        @Schema(description = "ACTIVA o INACTIVA", example = "INACTIVA")
        @NotBlank(message = "El estado no puede estar vacio")
        String estado
) {
}
