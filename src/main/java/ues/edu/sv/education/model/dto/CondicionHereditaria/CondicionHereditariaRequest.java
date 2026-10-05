package ues.edu.sv.education.model.dto.CondicionHereditaria;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ues.edu.sv.education.model.enums.Parentesco;

public record CondicionHereditariaRequest(
        @Schema(example = "Diabetes mellitus tipo 2")
        @NotBlank(message = "El nombre no puede estar vacío")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @Schema(description = "PADRE, MADRE, ABUELO, ABUELA, HERMANO, HERMANA u OTRO", example = "MADRE")
        @NotNull(message = "El parentesco es obligatorio")
        Parentesco parentesco,

        @Size(max = 255, message = "Las observaciones no pueden superar los 255 caracteres")
        String observaciones,

        @Schema(description = "persona_id del paciente", example = "12")
        @NotNull(message = "El paciente es obligatorio")
        Long pacienteId
) {}
