package ues.edu.sv.education.model.dto.cita;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Agendar una cita. El estado no viaja: toda cita nueva nace AGENDADA.
 */
public record CitaRequestDto(

        /** persona_id del paciente, el mismo id del expediente. */
        @NotNull(message = "El paciente es obligatorio")
        Long pacienteId,

        /** `yyyy-MM-ddTHH:mm`, hora local, en la grilla de 30 minutos. */
        @NotBlank(message = "La fecha y hora de la cita son obligatorias")
        String fechaHora,

        @NotNull(message = "El tipo de cita es obligatorio")
        Integer tipoCitaId,

        /** user_id del medico. */
        @NotNull(message = "El médico es obligatorio")
        Integer medicoId,

        @NotNull(message = "La clínica es obligatoria")
        Integer clinicaId

) {
}
