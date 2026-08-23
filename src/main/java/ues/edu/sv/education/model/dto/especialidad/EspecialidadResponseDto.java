package ues.edu.sv.education.model.dto.especialidad;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Especialidad medica del catalogo")
public record EspecialidadResponseDto(

        @Schema(description = "ID de la especialidad", example = "1")
        Long especialidadId,

        @Schema(description = "Nombre de la especialidad", example = "Medicina General")
        String nombre,

        @Schema(description = "Si la especialidad esta activa para nuevos registros", example = "true")
        boolean activa

) {
}
