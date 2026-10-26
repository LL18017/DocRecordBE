package ues.edu.sv.education.model.dto.prescripcion;

import io.swagger.v3.oas.annotations.media.Schema;
import ues.edu.sv.education.model.enums.SeveridadDeAlergia;

/**
 * Una coincidencia entre una alergia vigente del paciente y un medicamento
 * (HU-24): lo que el medico necesita leer antes de decidir.
 */
@Schema(description = "Alergia vigente del paciente que coincide con un medicamento")
public record AlertaDeAlergiaDto(

        @Schema(example = "4") Integer alergiaId,

        @Schema(example = "Penicilina") String sustancia,

        SeveridadDeAlergia severidad,

        @Schema(example = "Urticaria") String reaccion,

        @Schema(example = "12") Long medicamentoId,

        @Schema(description = "El medicamento tal como se lee en la receta") String medicamento

) {
}
