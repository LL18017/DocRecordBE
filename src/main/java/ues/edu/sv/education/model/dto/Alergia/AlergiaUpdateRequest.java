package ues.edu.sv.education.model.dto.Alergia;

import jakarta.validation.constraints.Size;

public record AlergiaUpdateRequest(

        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @Size(max = 100, message = "El tipo no puede superar los 100 caracteres")
        String tipo,

        @Size(max = 50, message = "La severidad no puede superar los 50 caracteres")
        String severidad,

        @Size(max = 255, message = "La reacción reportada no puede superar los 255 caracteres")
        String reaccionReportada
) {
}