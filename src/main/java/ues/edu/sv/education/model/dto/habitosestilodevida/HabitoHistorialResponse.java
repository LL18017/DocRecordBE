package ues.edu.sv.education.model.dto.habitosestilodevida;

import lombok.*;
import ues.edu.sv.education.model.dto.tipohabito.TipoHabitoResponse;
import ues.edu.sv.education.model.enums.NivelHabito;

import java.time.LocalDateTime;

/**
 * Una version anterior de un habito (HU-14, criterio 2). Los campos del
 * habito son los que tenia ENTONCES; `actualizadoEn/Por` dicen cuando y quien
 * escribio esa version, y `reemplazadoEn/Por` cuando y quien la cambio.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HabitoHistorialResponse {

    private Long historialId;
    private Long habitoId;
    private TipoHabitoResponse tipoHabito;
    private String descripcion;
    private NivelHabito nivel;
    private String frecuencia;
    private String observaciones;
    private LocalDateTime actualizadoEn;
    private String actualizadoPor;
    private LocalDateTime reemplazadoEn;
    private String reemplazadoPor;
}
