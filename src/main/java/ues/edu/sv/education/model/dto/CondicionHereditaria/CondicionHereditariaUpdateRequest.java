package ues.edu.sv.education.model.dto.CondicionHereditaria;

import jakarta.validation.constraints.Size;

public record CondicionHereditariaUpdateRequest(

        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @Size(max = 100, message = "El parentesco no puede superar los 100 caracteres")
        String parentesco,

        @Size(max = 255, message = "Las observaciones no pueden superar los 255 caracteres")
        String observaciones
) {}