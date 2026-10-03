package ues.edu.sv.education.model.dto.cita;

import jakarta.validation.constraints.NotNull;

public record CitaUpdateRequestDto(

        String fechaHora,

        Integer tipoCitaId,

        Integer medicoId,

        String estado

) {
}