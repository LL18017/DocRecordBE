package ues.edu.sv.education.model.dto.cita;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelarCitaRequestDto(

        @NotBlank(message = "El motivo de la cancelación es obligatorio")
        @Size(max = 255, message = "El motivo no puede pasar de 255 caracteres")
        String motivo

) {
}
