package ues.edu.sv.education.model.dto.CondicionHereditaria;

import jakarta.validation.constraints.Size;
import ues.edu.sv.education.model.enums.Parentesco;

/** Edicion parcial: lo que no viene, no cambia. */
public record CondicionHereditariaUpdateRequest(
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        Parentesco parentesco,

        @Size(max = 255, message = "Las observaciones no pueden superar los 255 caracteres")
        String observaciones
) {}
