package ues.edu.sv.education.model.mappers;

import org.springframework.stereotype.Component;
import ues.edu.sv.education.model.dto.cita.CitaResponseDto;
import ues.edu.sv.education.model.entity.Cita;
import ues.edu.sv.education.model.entity.Persona;

@Component
public class CitaMapper {

    public CitaResponseDto toResponse(Cita entity) {

        if (entity == null) {
            return null;
        }

        return new CitaResponseDto(
                entity.getCitaId(),
                entity.getFechaHora(),
                entity.getTipo() != null ? entity.getTipo().getTipoCitaId() : null,
                entity.getTipo() != null ? entity.getTipo().getNombre() : null,
                entity.getPaciente() != null ? entity.getPaciente().getPersonaId() : null,
                entity.getPaciente() != null ? nombreCompleto(entity.getPaciente().getPersona()) : null,
                entity.getPaciente() != null ? entity.getPaciente().getExpediente() : null,
                entity.getMedico() != null ? entity.getMedico().getUserID() : null,
                entity.getMedico() != null ? nombreCompleto(entity.getMedico().getPersona()) : null,
                entity.getClinica() != null ? entity.getClinica().getClinicaId() : null,
                entity.getClinica() != null ? entity.getClinica().getName() : null,
                entity.getEstado(),
                entity.getMotivoCancelacion()
        );
    }

    private static String nombreCompleto(Persona persona) {
        if (persona == null) {
            return null;
        }
        return (persona.getNombres() + " " + persona.getApellidos()).trim();
    }
}
