package ues.edu.sv.education.model.mappers;

import org.springframework.stereotype.Component;
import ues.edu.sv.education.model.dto.habitosestilodevida.HabitosEstiloDeVidaRequest;
import ues.edu.sv.education.model.dto.habitosestilodevida.HabitosEstiloDeVidaResponse;
import ues.edu.sv.education.model.dto.tipohabito.TipoHabitoResponse;
import ues.edu.sv.education.model.entity.HabitosEstiloDeVida;
import ues.edu.sv.education.model.entity.Paciente;
import ues.edu.sv.education.model.entity.TipoHabito;

@Component
public class HabitosEstiloDeVidaMapper {

    private final TipoHabitoMapper tipoHabitoMapper;

    public HabitosEstiloDeVidaMapper(TipoHabitoMapper tipoHabitoMapper) {
        this.tipoHabitoMapper = tipoHabitoMapper;
    }

    public HabitosEstiloDeVida toEntity(
            HabitosEstiloDeVidaRequest request,
            Paciente paciente,
            TipoHabito tipoHabito) {

        if (request == null) {
            return null;
        }

        return HabitosEstiloDeVida.builder()
                .descripcion(request.getDescripcion())
                .nivel(request.getNivel())
                .paciente(paciente)
                .tipoHabito(tipoHabito)
                .build();
    }

    public HabitosEstiloDeVidaResponse toResponse(
            HabitosEstiloDeVida entity) {

        if (entity == null) {
            return null;
        }

        TipoHabitoResponse tipoHabitoResponse =
                tipoHabitoMapper.toResponse(entity.getTipoHabito());

        return HabitosEstiloDeVidaResponse.builder()
                .id(entity.getId())
                .descripcion(entity.getDescripcion())
                .nivel(entity.getNivel())
                .pacienteId(entity.getPaciente().getPersonaId())
                .tipoHabito(tipoHabitoResponse)
                .build();
    }
}