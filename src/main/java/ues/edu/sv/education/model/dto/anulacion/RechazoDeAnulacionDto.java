package ues.edu.sv.education.model.dto.anulacion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Por que no se anula: queda en la solicitud para quien la pidio. */
@Schema(description = "Datos para rechazar una solicitud de anulacion")
public record RechazoDeAnulacionDto(

        @Schema(description = "Por que se rechaza (10 a 500 caracteres)",
                example = "La consulta es correcta; corrija solo el motivo con la edicion")
        @NotBlank(message = "El motivo del rechazo es obligatorio")
        @Size(min = 10, max = 500, message = "El motivo del rechazo debe tener entre 10 y 500 caracteres")
        String motivo

) {
}
