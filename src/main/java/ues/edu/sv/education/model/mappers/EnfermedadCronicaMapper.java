package ues.edu.sv.education.model.mappers;

import org.springframework.util.StringUtils;
import ues.edu.sv.education.model.dto.EnfermedadCronica.EnfermedadCronicaRequest;
import ues.edu.sv.education.model.dto.EnfermedadCronica.EnfermedadCronicaResponse;
import ues.edu.sv.education.model.dto.EnfermedadCronica.EnfermedadCronicaUpdateRequest;
import ues.edu.sv.education.model.entity.EnfermedadCronica;
import ues.edu.sv.education.model.entity.User;

public class EnfermedadCronicaMapper {

    private EnfermedadCronicaMapper() {
    }

    public static EnfermedadCronica toEntity(
            EnfermedadCronicaRequest request,
            User user
    ) {
        return EnfermedadCronica.builder()
                .nombre(request.nombre())
                .anio(request.anio())
                .tratamiento(request.tratamiento())
                .user(user)
                .build();
    }

    public static void updateEntity(
            EnfermedadCronica enfermedadCronica,
            EnfermedadCronicaUpdateRequest request
    ) {
        if (StringUtils.hasText(request.nombre())) {
            enfermedadCronica.setNombre(request.nombre());
        }

        if (request.anio() != null) {
            enfermedadCronica.setAnio(request.anio());
        }

        if (StringUtils.hasText(request.tratamiento())) {
            enfermedadCronica.setTratamiento(request.tratamiento());
        }
    }

    public static EnfermedadCronicaResponse toResponse(
            EnfermedadCronica enfermedadCronica
    ) {
        return new EnfermedadCronicaResponse(
                enfermedadCronica.getEnfermedadCronicaID(),
                enfermedadCronica.getNombre(),
                enfermedadCronica.getAnio(),
                enfermedadCronica.getTratamiento(),
                enfermedadCronica.getUser().getName()
        );
    }
}