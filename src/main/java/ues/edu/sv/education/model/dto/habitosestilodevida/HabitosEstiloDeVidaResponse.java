package ues.edu.sv.education.model.dto.habitosestilodevida;

import lombok.*;
import ues.edu.sv.education.model.dto.tipohabito.TipoHabitoResponse;
import ues.edu.sv.education.model.enums.NivelHabito;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HabitosEstiloDeVidaResponse {

    private Long id;
    private String descripcion;
    private ues.edu.sv.education.model.enums.NivelHabito nivel;
    private Long pacienteId;
    private TipoHabitoResponse tipoHabito;
}