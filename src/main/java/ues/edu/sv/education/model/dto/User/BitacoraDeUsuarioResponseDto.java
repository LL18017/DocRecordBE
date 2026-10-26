package ues.edu.sv.education.model.dto.User;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Una acción de administración registrada sobre una cuenta")
public record BitacoraDeUsuarioResponseDto(

        @Schema(description = "ALTA, ACTIVACION, DESACTIVACION, ROL_ASIGNADO o ROL_RETIRADO",
                example = "ROL_ASIGNADO")
        String accion,

        @Schema(description = "Detalle de la acción, p. ej. el rol afectado", example = "Rol MEDICO")
        String detalle,

        @Schema(description = "Correo de quien la realizó", example = "admin@docrecord.sv")
        String realizadoPor,

        @Schema(description = "Cuándo se realizó")
        LocalDateTime realizadoEn

) {
}
