package ues.edu.sv.education.model.dto.EnfermedadCronica;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EnfermedadCronicaRequest(

        @NotBlank(message = "El nombre de la enfermedad no puede estar vacío")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @NotNull(message = "El año es obligatorio")
        Integer anio,

        @NotBlank(message = "El tratamiento no puede estar vacío")
        @Size(max = 255, message = "El tratamiento no puede superar los 255 caracteres")
        String tratamiento,

        @NotNull(message = "El usuario es obligatorio")
        Integer userId
) {
}