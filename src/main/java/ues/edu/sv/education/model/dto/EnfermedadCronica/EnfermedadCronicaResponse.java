package ues.edu.sv.education.model.dto.EnfermedadCronica;

public record EnfermedadCronicaResponse(
        Integer enfermedadCronicaID,
        String nombre,
        Integer anio,
        String tratamiento,
        String userName
) {
}