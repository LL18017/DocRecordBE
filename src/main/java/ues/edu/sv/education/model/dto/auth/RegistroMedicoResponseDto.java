package ues.edu.sv.education.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import ues.edu.sv.education.model.dto.especialidad.EspecialidadResponseDto;

import java.util.List;

@Schema(description = "Cuenta de medico recien registrada")
public record RegistroMedicoResponseDto(

        @Schema(description = "ID del usuario", example = "7")
        Integer userId,

        @Schema(description = "Correo electronico", example = "juan.guerra@docrecord.sv")
        String email,

        @Schema(description = "Nombres de la persona", example = "Juan Armando")
        String nombres,

        @Schema(description = "Apellidos de la persona", example = "Guerra Guevara")
        String apellidos,

        @Schema(description = "Roles asignados", example = "[\"MEDICO\"]")
        List<String> roles,

        @Schema(description = "Especialidad medica")
        EspecialidadResponseDto especialidad

) {
}
