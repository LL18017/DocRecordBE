package ues.edu.sv.education.model.dto.prescripcion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * La decision del medico ante una alerta de alergia que NO termina en receta
 * (HU-24, criterio 5). Cuando si la termina, la decision queda en la propia
 * receta (justificacion) y en el evento ALLERGY_ALERT_OVERRIDE.
 * <p>
 * Quien decide sale del token, no del cuerpo.
 */
@Schema(description = "Decision del medico ante una alerta de alergia mostrada")
public record DecisionDeAlertaDto(

        @NotNull(message = "El paciente es obligatorio")
        @Schema(example = "7") Long pacienteId,

        @NotNull(message = "El medicamento es obligatorio")
        @Schema(example = "12") Long medicamentoId,

        @NotNull(message = "La decisión es obligatoria")
        @Schema(example = "DESISTIO") Decision decision

) {

    /** Lista cerrada: hoy solo se registra el desistimiento. */
    public enum Decision {
        DESISTIO
    }
}
