package ues.edu.sv.education.model.dto.habitosestilodevida;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo de la anulacion de un habito (HU-14, DEF-03).
 *
 * Solo el motivo: quien anula sale del token y la fecha la pone el servidor.
 * Sin @NotBlank a proposito: el servicio valida el largo (10 a 500 tras
 * recortar) y responde un unico mensaje para vacio, corto o largo.
 */
@Schema(description = "Motivo por el que se anula un habito")
public record AnularHabitoRequest(

        @Schema(description = "Por que el habito no vale (10 a 500 caracteres)",
                example = "Se registró en el paciente equivocado")
        String motivo

) {
}
