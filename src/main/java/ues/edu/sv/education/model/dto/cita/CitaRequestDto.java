package ues.edu.sv.education.model.dto.cita;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CitaRequestDto(

        @NotNull(message = "El paciente es obligatorio")
        Integer pacienteId,

        @NotNull(message = "La fecha y hora de la cita son obligatorias")
        String fechaHora,

        @NotNull(message = "El tipo de cita es obligatorio")
        Integer tipoCitaId,

        @NotNull(message = "El médico es obligatorio")
        Integer medicoId,

        @NotNull(message = "El estado de la cita es obligatorio")
        String estado

) {
}