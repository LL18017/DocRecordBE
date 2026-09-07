package ues.edu.sv.education.model.dto.Alergia;

public record AlergiaResponse(
        Integer alergiaID,
        String nombre,
        String tipo,
        String severidad,
        String reaccionReportada,
        Integer userId
) {
}