package ues.edu.sv.education.model.dto.CondicionHereditaria;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CondicionHereditariaRequest(

        @NotBlank(message = "El nombre no puede estar vacío")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @NotBlank(message = "El parentesco no puede estar vacío")
        @Size(max = 100, message = "El parentesco no puede superar los 100 caracteres")
        String parentesco,

        @Size(max = 255, message = "Las observaciones no pueden superar los 255 caracteres")
        String observaciones,

        @NotNull(message = "El usuario es obligatorio")
        Integer userId
) {}