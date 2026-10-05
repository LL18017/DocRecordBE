package ues.edu.sv.education.model.dto.prescripcion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Un renglon de la receta.
 *
 * El medicamento se elige del catalogo por su id, ya no se escribe (HU-23
 * criterio 3: "no acepta valores fuera de el"). Es lo unico obligatorio: hay
 * indicaciones sin dosis ni duracion. Un renglon sin medicamento, en cambio,
 * no indica nada.
 *
 * No hay campo de nombre: lo pone el servidor desde el catalogo. Si lo
 * mandara el cliente, la receta podria decir "Amoxicilina" apuntando a la
 * fila del ibuprofeno.
 */
@Schema(description = "Medicamento indicado dentro de una receta")
public record MedicamentoRequestDto(

        @Schema(description = "Id del medicamento en el catalogo (GET /medicamentos). Debe estar activo",
                example = "6")
        @NotNull(message = "Elige el medicamento del catalogo")
        Long medicamentoId,

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
