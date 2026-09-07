package ues.edu.sv.education.model.dto.Alergia;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AlergiaRequest(

        @NotBlank(message = "El nombre de la alergia no puede estar vacío")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @NotBlank(message = "El tipo de alergia no puede estar vacío")
        @Size(max = 100, message = "El tipo no puede superar los 100 caracteres")
        String tipo,

        @NotBlank(message = "La severidad no puede estar vacía")
        @Size(max = 50, message = "La severidad no puede superar los 50 caracteres")
        String severidad,

        @NotBlank(message = "La reacción reportada no puede estar vacía")
        @Size(max = 255, message = "La reacción reportada no puede superar los 255 caracteres")
        String reaccionReportada,

        @NotNull(message = "El usuario es obligatorio")
        Integer userId
) {
}