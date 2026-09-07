package ues.edu.sv.education.model.dto.tipoCita;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TipoCitaRequestDto(

        @NotBlank
        @Size(max = 100)
        String nombre,

        @Size(max = 255)
        String descripcion
) {
}