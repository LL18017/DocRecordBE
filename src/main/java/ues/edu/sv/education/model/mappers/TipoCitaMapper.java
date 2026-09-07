package ues.edu.sv.education.model.mappers;

import org.springframework.stereotype.Component;
import ues.edu.sv.education.model.dto.tipoCita.TipoCitaRequestDto;
import ues.edu.sv.education.model.dto.tipoCita.TipoCitaResponseDto;
import ues.edu.sv.education.model.entity.TipoCita;

@Component
public class TipoCitaMapper {

    public TipoCita toEntity(TipoCitaRequestDto dto) {
        if (dto == null) {
            return null;
        }

        return TipoCita.builder()
                .nombre(dto.nombre())
                .descripcion(dto.descripcion())
                .build();
    }

    public TipoCitaResponseDto toResponse(TipoCita entity) {
        if (entity == null) {
            return null;
        }

        return new TipoCitaResponseDto(
                entity.getTipoCitaId(),
                entity.getNombre(),
                entity.getDescripcion()
        );
    }
}