package ues.edu.sv.education.model.mappers;

import org.springframework.stereotype.Component;
import ues.edu.sv.education.model.dto.tipohabito.TipoHabitoRequest;
import ues.edu.sv.education.model.dto.tipohabito.TipoHabitoResponse;
import ues.edu.sv.education.model.entity.TipoHabito;

@Component
public class TipoHabitoMapper {

    public TipoHabito toEntity(TipoHabitoRequest request) {
        if (request == null) {
            return null;
        }

        return TipoHabito.builder()
                .descripcion(request.getDescripcion())
                .build();
    }

    public TipoHabitoResponse toResponse(TipoHabito entity) {
        if (entity == null) {
            return null;
        }

        return TipoHabitoResponse.builder()
                .id(entity.getId())
                .descripcion(entity.getDescripcion())
                .build();
    }
}