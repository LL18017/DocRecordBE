package ues.edu.sv.education.model.dto.prescripcion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Un renglon de la receta.
 *
 * El medicamento se elige del catalogo por su id, ya no se escribe (HU-23
 * criterio 3: "no acepta valores fuera de el").
 *
 * HU-22, criterio 2: dosis, via de administracion, frecuencia y duracion son
 * OBLIGATORIAS. No llevan @NotBlank a proposito: PrescripcionService las
 * comprueba despues de saber que quien emite es el medico, para que una
 * enfermera con un renglon incompleto reciba 403 y no 400 (la validacion del
 * cuerpo corre antes que la autorizacion).
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

        @Schema(description = "Obligatoria", example = "1 tableta")
        @Size(max = 80, message = "La dosis no puede superar los 80 caracteres")
        String dosis,

        @Schema(description = "Obligatoria", example = "Oral")
        @Size(max = 60, message = "La vía de administración no puede superar los 60 caracteres")
        String viaAdministracion,

        @Schema(description = "Obligatoria", example = "Cada 8 horas")
        @Size(max = 80, message = "La frecuencia no puede superar los 80 caracteres")
        String frecuencia,

        @Schema(description = "Obligatoria", example = "7 dias")
        @Size(max = 80, message = "La duracion no puede superar los 80 caracteres")
        String duracion

) {
}
