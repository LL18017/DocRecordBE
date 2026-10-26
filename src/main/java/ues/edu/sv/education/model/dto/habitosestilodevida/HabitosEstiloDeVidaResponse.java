package ues.edu.sv.education.model.dto.habitosestilodevida;

import lombok.*;
import ues.edu.sv.education.model.dto.tipohabito.TipoHabitoResponse;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HabitosEstiloDeVidaResponse {

    private Long id;
    private String descripcion;
    private ues.edu.sv.education.model.enums.NivelHabito nivel;
    private String frecuencia;
    private String observaciones;
    private LocalDateTime actualizadoEn;
    // Nombre de quien lo registro o edito por ultima vez; null en los
    // habitos anteriores a que se guardara el autor.
    private String actualizadoPor;
    private Long pacienteId;
    private TipoHabitoResponse tipoHabito;
}
