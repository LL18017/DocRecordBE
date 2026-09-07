package ues.edu.sv.education.model.dto.CondicionHereditaria;

public record CondicionHereditariaResponse(
        Integer condicionHereditariaID,
        String nombre,
        String parentesco,
        String observaciones,
        Integer userId
) {}