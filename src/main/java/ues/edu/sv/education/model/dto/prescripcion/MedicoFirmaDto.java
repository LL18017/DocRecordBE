package ues.edu.sv.education.model.dto.prescripcion;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * El medico que firma la receta.
 *
 * Sin especialidad, a diferencia del medico de una consulta: lo que hace
 * valida a una receta es quien la firma y su numero de junta de vigilancia
 * (HU-22, criterio 4), no en que se especializa.
 */
@Schema(description = "Medico responsable de la receta")
public record MedicoFirmaDto(
        @Schema(example = "7") Long personaId,
        @Schema(example = "Carlos") String nombres,
        @Schema(example = "Menjívar") String apellidos,
        @Schema(description = "Numero de junta de vigilancia; null si el medico no lo ha registrado", example = "JVPM-12345")
        String registroJunta
) {
}
