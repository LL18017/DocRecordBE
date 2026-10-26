package ues.edu.sv.education.model.mappers;

import org.springframework.stereotype.Component;
import ues.edu.sv.education.model.dto.habitosestilodevida.HabitoHistorialResponse;
import ues.edu.sv.education.model.dto.habitosestilodevida.HabitosEstiloDeVidaRequest;
import ues.edu.sv.education.model.dto.habitosestilodevida.HabitosEstiloDeVidaResponse;
import ues.edu.sv.education.model.dto.tipohabito.TipoHabitoResponse;
import ues.edu.sv.education.model.entity.HabitoHistorial;
import ues.edu.sv.education.model.entity.HabitosEstiloDeVida;
import ues.edu.sv.education.model.entity.Paciente;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.TipoHabito;

import java.time.LocalDateTime;

@Component
public class HabitosEstiloDeVidaMapper {

    private final TipoHabitoMapper tipoHabitoMapper;

    public HabitosEstiloDeVidaMapper(TipoHabitoMapper tipoHabitoMapper) {
        this.tipoHabitoMapper = tipoHabitoMapper;
    }

    public HabitosEstiloDeVida toEntity(
            HabitosEstiloDeVidaRequest request,
            Paciente paciente,
            TipoHabito tipoHabito,
            Persona autor,
            LocalDateTime ahora) {

        if (request == null) {
            return null;
        }

        return HabitosEstiloDeVida.builder()
                .descripcion(request.getDescripcion())
                .nivel(request.getNivel())
                .frecuencia(request.getFrecuencia().trim())
                .observaciones(textoONull(request.getObservaciones()))
                .actualizadoEn(ahora)
                .actualizadoPor(autor)
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
                .frecuencia(entity.getFrecuencia())
                .observaciones(entity.getObservaciones())
                .actualizadoEn(entity.getActualizadoEn())
                .actualizadoPor(nombreDe(entity.getActualizadoPor()))
                .pacienteId(entity.getPaciente().getPersonaId())
                .tipoHabito(tipoHabitoResponse)
                .build();
    }

    /**
     * La version que se va a reemplazar, tal como estaba, anotando quien y
     * cuando la reemplaza. Se llama ANTES de sobrescribir el habito.
     */
    public HabitoHistorial toHistorial(
            HabitosEstiloDeVida anterior,
            Persona reemplazadoPor,
            LocalDateTime ahora) {

        return HabitoHistorial.builder()
                .habito(anterior)
                .tipoHabito(anterior.getTipoHabito())
                .descripcion(anterior.getDescripcion())
                .nivel(anterior.getNivel())
                .frecuencia(anterior.getFrecuencia())
                .observaciones(anterior.getObservaciones())
                .actualizadoEn(anterior.getActualizadoEn())
                .actualizadoPor(anterior.getActualizadoPor())
                .reemplazadoEn(ahora)
                .reemplazadoPor(reemplazadoPor)
                .build();
    }

    public HabitoHistorialResponse toHistorialResponse(HabitoHistorial entity) {
        return HabitoHistorialResponse.builder()
                .historialId(entity.getHistorialId())
                .habitoId(entity.getHabito().getId())
                .tipoHabito(tipoHabitoMapper.toResponse(entity.getTipoHabito()))
                .descripcion(entity.getDescripcion())
                .nivel(entity.getNivel())
                .frecuencia(entity.getFrecuencia())
                .observaciones(entity.getObservaciones())
                .actualizadoEn(entity.getActualizadoEn())
                .actualizadoPor(nombreDe(entity.getActualizadoPor()))
                .reemplazadoEn(entity.getReemplazadoEn())
                .reemplazadoPor(nombreDe(entity.getReemplazadoPor()))
                .build();
    }

    public static String textoONull(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    private static String nombreDe(Persona persona) {
        return persona == null ? null : persona.getNombres() + " " + persona.getApellidos();
    }
}
