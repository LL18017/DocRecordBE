package ues.edu.sv.education.model.dto.EnfermedadCronica;

import jakarta.validation.constraints.Size;

public record EnfermedadCronicaUpdateRequest(

        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,
        Integer anio,
        @Size(max = 255, message = "El tratamiento no puede superar los 255 caracteres")
        String tratamiento
) {
}