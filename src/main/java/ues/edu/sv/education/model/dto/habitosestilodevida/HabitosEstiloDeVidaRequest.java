package ues.edu.sv.education.model.dto.habitosestilodevida;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import ues.edu.sv.education.model.enums.NivelHabito;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HabitosEstiloDeVidaRequest {

    @NotBlank(message = "La descripción es obligatoria")
    @Size(max = 255, message = "La descripción no puede superar 255 caracteres")
    private String descripcion;

    @NotNull(message = "El nivel es obligatorio")
    private NivelHabito nivel;

    @NotNull(message = "El ID del paciente es obligatorio")
    private Long pacienteId;

    @NotNull(message = "El ID del tipo de hábito es obligatorio")
    private Integer tipoHabitoId;
}