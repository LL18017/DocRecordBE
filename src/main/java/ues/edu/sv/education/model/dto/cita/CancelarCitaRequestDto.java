package ues.edu.sv.education.model.dto.cita;

import jakarta.validation.constraints.NotBlank;

public record CancelarCitaRequestDto(

        // El largo (10 a 500, sin espacios de los bordes) lo valida el servicio con
        // un solo mensaje: "El motivo debe tener entre 10 y 500 caracteres".
        @NotBlank(message = "El motivo de la cancelación es obligatorio")
        String motivo

) {
}
