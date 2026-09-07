package ues.edu.sv.education.model.mappers;

import ues.edu.sv.education.model.dto.Alergia.AlergiaRequest;
import ues.edu.sv.education.model.dto.Alergia.AlergiaResponse;
import ues.edu.sv.education.model.dto.Alergia.AlergiaUpdateRequest;
import ues.edu.sv.education.model.entity.Alergia;
import ues.edu.sv.education.model.entity.User;

public class AlergiaMapper {

    private AlergiaMapper() {
    }

    public static Alergia toEntity(
            AlergiaRequest request,
            User user
    ) {
        return Alergia.builder()
                .nombre(request.nombre())
                .tipo(request.tipo())
                .severidad(request.severidad())
                .reaccionReportada(request.reaccionReportada())
                .user(user)
                .build();
    }

    public static void updateEntity(
            Alergia alergia,
            AlergiaUpdateRequest request
    ) {
        if (request.nombre() != null && !request.nombre().isBlank()) {
            alergia.setNombre(request.nombre());
        }

        if (request.tipo() != null && !request.tipo().isBlank()) {
            alergia.setTipo(request.tipo());
        }

        if (request.severidad() != null && !request.severidad().isBlank()) {
            alergia.setSeveridad(request.severidad());
        }

        if (request.reaccionReportada() != null
                && !request.reaccionReportada().isBlank()) {
            alergia.setReaccionReportada(request.reaccionReportada());
        }
    }

    public static AlergiaResponse toResponse(
            Alergia alergia
    ) {
        return new AlergiaResponse(
                alergia.getAlergiaID(),
                alergia.getNombre(),
                alergia.getTipo(),
                alergia.getSeveridad(),
                alergia.getReaccionReportada(),
                alergia.getUser().getUserID()
        );
    }
}
