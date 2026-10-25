package ues.edu.sv.education.model.dto.cita;

/** Un medico al que se le puede agendar en una sede. `medicoId` es su user_id. */
public record MedicoDeAgendaDto(Integer medicoId, String nombre) {
}
