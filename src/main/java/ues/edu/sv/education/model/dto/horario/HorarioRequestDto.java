package ues.edu.sv.education.model.dto.horario;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Una franja semanal: dia (1 = lunes ... 7 = domingo) y horas `HH:mm`. */
public record HorarioRequestDto(

        @NotNull(message = "El día es obligatorio")
        @Min(value = 1, message = "El día va de 1 (lunes) a 7 (domingo)")
        @Max(value = 7, message = "El día va de 1 (lunes) a 7 (domingo)")
        Integer diaSemana,

        @NotBlank(message = "La hora de inicio es obligatoria")
        String horaInicio,

        @NotBlank(message = "La hora de fin es obligatoria")
        String horaFin
) {
}
