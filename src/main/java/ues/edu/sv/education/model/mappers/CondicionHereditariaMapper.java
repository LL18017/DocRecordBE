package ues.edu.sv.education.model.mappers;

import ues.edu.sv.education.model.dto.CondicionHereditaria.CondicionHereditariaRequest;
import ues.edu.sv.education.model.dto.CondicionHereditaria.CondicionHereditariaResponse;
import ues.edu.sv.education.model.dto.CondicionHereditaria.CondicionHereditariaUpdateRequest;
import ues.edu.sv.education.model.entity.CondicionHereditaria;
import ues.edu.sv.education.model.entity.User;

public class CondicionHereditariaMapper {

    private CondicionHereditariaMapper() {}

    public static CondicionHereditaria toEntity(
            CondicionHereditariaRequest request,
            User user
    ) {
        return CondicionHereditaria.builder()
                .nombre(request.nombre())
                .parentesco(request.parentesco())
                .observaciones(request.observaciones())
                .user(user)
                .build();
    }

    public static void updateEntity(
            CondicionHereditaria condicionHereditaria,
            CondicionHereditariaUpdateRequest request
    ) {
        if (request.nombre() != null && !request.nombre().isBlank()) {
            condicionHereditaria.setNombre(request.nombre());
        }

        if (request.parentesco() != null && !request.parentesco().isBlank()) {
            condicionHereditaria.setParentesco(request.parentesco());
        }

        if (request.observaciones() != null && !request.observaciones().isBlank()) {
            condicionHereditaria.setObservaciones(request.observaciones());
        }
    }

    public static CondicionHereditariaResponse toResponse(
            CondicionHereditaria condicionHereditaria
    ) {
        return new CondicionHereditariaResponse(
                condicionHereditaria.getCondicionHereditariaID(),
                condicionHereditaria.getNombre(),
                condicionHereditaria.getParentesco(),
                condicionHereditaria.getObservaciones(),
                condicionHereditaria.getUser().getUserID()
        );
    }
}