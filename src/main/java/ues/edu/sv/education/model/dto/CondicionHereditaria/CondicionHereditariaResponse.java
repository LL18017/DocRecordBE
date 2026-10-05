package ues.edu.sv.education.model.dto.CondicionHereditaria;

import ues.edu.sv.education.model.enums.Parentesco;

public record CondicionHereditariaResponse(
        Integer condicionHereditariaId,
        Long pacienteId,
        String nombre,
        Parentesco parentesco,
        String observaciones
) {}
