package ues.edu.sv.education.model.mappers;

import org.springframework.stereotype.Component;
import ues.edu.sv.education.model.dto.cita.CitaRequestDto;
import ues.edu.sv.education.model.dto.cita.CitaResponseDto;
import ues.edu.sv.education.model.entity.Cita;
import ues.edu.sv.education.model.entity.TipoCita;
import ues.edu.sv.education.model.entity.User;

import java.time.LocalDateTime;

@Component
public class CitaMapper {

    public Cita toEntity(
            CitaRequestDto dto,
            User paciente,
            TipoCita tipoCita,
            User medico
    ) {
        if (dto == null) {
            return null;
        }
        LocalDateTime fecha = LocalDateTime.parse(dto.fechaHora());
        return Cita.builder()
                .paciente(paciente)
                .fechaHora(fecha)
                .tipo(tipoCita)
                .medico(medico)
                .estado(dto.estado())
                .build();
    }

    public CitaResponseDto toResponse(Cita entity) {

        if (entity == null) {
            return null;
        }

        return new CitaResponseDto(
                entity.getCitaId(),
                entity.getFechaHora(),
                                entity.getTipo() != null
                        ? entity.getTipo().getNombre()
                        : null,

                entity.getMedico() != null
                        ? entity.getMedico().getName()
                        : null,

                entity.getEstado() != null
                        ? entity.getEstado()
                        : null
        );
    }
}