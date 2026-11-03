package ues.edu.sv.education.model.dto.anulacion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Los mismos limites que los CHECK de parametros_de_anulacion (V35). */
@Schema(description = "Nuevas reglas de anulacion de un tipo de documento")
public record ParametroDeAnulacionRequestDto(

        @Schema(description = "0 a 1440 (un dia)", example = "10")
        @NotNull(message = "Los minutos de anulación directa son obligatorios")
        @Min(value = 0, message = "Los minutos de anulación directa no pueden ser negativos")
        @Max(value = 1440, message = "Los minutos de anulación directa no pueden pasar de 1440 (un día)")
        Integer minutosAnulacionDirecta,

        @Schema(description = "1 a 5", example = "2")
        @NotNull(message = "El número de aprobaciones es obligatorio")
        @Min(value = 1, message = "Hace falta al menos una aprobación")
        @Max(value = 5, message = "No se pueden exigir más de 5 aprobaciones")
        Integer aprobacionesRequeridas,

        @Schema(description = "1 a 3650, o null para no poner tope", example = "30")
        @Min(value = 1, message = "El tope de días debe ser de al menos 1")
        @Max(value = 3650, message = "El tope de días no puede pasar de 3650")
        Integer diasMaximos

) {
}
