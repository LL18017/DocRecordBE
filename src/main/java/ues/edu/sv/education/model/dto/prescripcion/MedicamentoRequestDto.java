package ues.edu.sv.education.model.dto.prescripcion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Un renglon de la receta.
 *
 * Solo el nombre del medicamento es obligatorio: hay indicaciones sin dosis ni
 * duracion ("suspender el tratamiento anterior"). Un renglon sin nombre, en
 * cambio, no indica nada.
 */
@Schema(description = "Medicamento indicado dentro de una receta")
public record MedicamentoRequestDto(

        @Schema(description = "Nombre del medicamento", example = "Amoxicilina 500 mg")
        @NotBlank(message = "El nombre del medicamento no puede estar vacio")
        @Size(max = 160, message = "El medicamento no puede superar los 160 caracteres")
        String medicamento,

        @Schema(example = "1 tableta")
        @Size(max = 80, message = "La dosis no puede superar los 80 caracteres")
        String dosis,

        @Schema(example = "Cada 8 horas")
        @Size(max = 80, message = "La frecuencia no puede superar los 80 caracteres")
        String frecuencia,

        @Schema(example = "7 dias")
        @Size(max = 80, message = "La duracion no puede superar los 80 caracteres")
        String duracion

) {
}
