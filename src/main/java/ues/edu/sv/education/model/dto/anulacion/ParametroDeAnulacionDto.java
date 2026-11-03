package ues.edu.sv.education.model.dto.anulacion;

import io.swagger.v3.oas.annotations.media.Schema;
import ues.edu.sv.education.model.enums.TipoDocumentoAnulable;

import java.time.LocalDateTime;

@Schema(description = "Reglas de anulacion de un tipo de documento")
public record ParametroDeAnulacionDto(

        TipoDocumentoAnulable tipo,

        @Schema(description = "Minutos desde que se registro en los que se anula sin aprobacion. 0 = siempre con aprobacion",
                example = "10")
        Integer minutosAnulacionDirecta,

        @Schema(description = "Administradores distintos de quien la pide que tienen que aprobar", example = "2")
        Integer aprobacionesRequeridas,

        @Schema(description = "Dias despues de los cuales ya no se anula; null = sin tope", example = "30")
        Integer diasMaximos,

        LocalDateTime actualizadoEn,

        @Schema(description = "Nombre de quien la cambio por ultima vez; null si sigue la de fabrica")
        String actualizadoPor

) {
}
