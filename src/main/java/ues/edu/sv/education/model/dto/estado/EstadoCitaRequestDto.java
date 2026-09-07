package ues.edu.sv.education.model.dto.estado;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EstadoCitaRequestDto(

        @NotBlank
        @Size(max = 50)
        String nombre,

        @Size(max = 255)
        String descripcion
) {
}