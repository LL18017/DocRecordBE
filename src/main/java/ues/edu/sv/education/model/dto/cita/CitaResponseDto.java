package ues.edu.sv.education.model.dto.cita;

import java.time.LocalDateTime;

public record CitaResponseDto(

        Integer citaId,

        LocalDateTime fechaHora,

        String tipoCita,


        String medico,

        String estado
) {
}