package ues.edu.sv.education.model.mappers;

import ues.edu.sv.education.model.dto.CondicionHereditaria.CondicionHereditariaRequest;
import ues.edu.sv.education.model.dto.CondicionHereditaria.CondicionHereditariaResponse;
import ues.edu.sv.education.model.dto.CondicionHereditaria.CondicionHereditariaUpdateRequest;
import ues.edu.sv.education.model.entity.CondicionHereditaria;
import ues.edu.sv.education.model.entity.Paciente;

public class CondicionHereditariaMapper {

    private CondicionHereditariaMapper() {}

    public static CondicionHereditaria toEntity(CondicionHereditariaRequest request, Paciente paciente) {
        return CondicionHereditaria.builder()
                .nombre(request.nombre().trim())
                .parentesco(request.parentesco())
                .observaciones(textoOpcional(request.observaciones()))
                .paciente(paciente)
                .build();
    }

    public static void updateEntity(CondicionHereditaria condicion, CondicionHereditariaUpdateRequest request) {
        if (request.nombre() != null && !request.nombre().isBlank()) {
            condicion.setNombre(request.nombre().trim());
        }
        if (request.parentesco() != null) {
            condicion.setParentesco(request.parentesco());
        }
        if (request.observaciones() != null) {
            condicion.setObservaciones(textoOpcional(request.observaciones()));
        }
    }

    public static CondicionHereditariaResponse toResponse(CondicionHereditaria condicion) {
        return new CondicionHereditariaResponse(
                condicion.getCondicionHereditariaId(),
                condicion.getPaciente().getPersonaId(),
                condicion.getNombre(),
                condicion.getParentesco(),
                condicion.getObservaciones()
        );
    }

    /** Unas observaciones en blanco no son observaciones: se guardan como null. */
    private static String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
