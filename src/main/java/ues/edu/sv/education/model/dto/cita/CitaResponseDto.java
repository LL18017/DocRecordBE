package ues.edu.sv.education.model.dto.cita;

import java.time.LocalDateTime;

public record CitaResponseDto(

        Integer citaId,

        LocalDateTime fechaHora,

        Integer tipoCitaId,

        String tipoCita,

        /** persona_id del paciente. */
        Long pacienteId,

        String paciente,

        String expediente,

        /** user_id del medico. */
        Integer medicoId,

        String medico,

        /** Anulable en citas anteriores a V24. */
        Integer clinicaId,

        String clinica,

        String estado,

        String motivoCancelacion
) {
}
