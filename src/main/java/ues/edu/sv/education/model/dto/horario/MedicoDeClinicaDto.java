package ues.edu.sv.education.model.dto.horario;

import java.util.List;

/** Un medico de la sede con su especialidad y sus franjas en ELLA (HU-29, criterio 3). */
public record MedicoDeClinicaDto(Integer medicoId, String nombre, String especialidad,
                                 List<HorarioResponseDto> horarios) {
}
