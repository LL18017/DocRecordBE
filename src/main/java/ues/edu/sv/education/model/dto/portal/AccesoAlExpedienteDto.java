package ues.edu.sv.education.model.dto.portal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Una vez que se abrio o se descargo el expediente desde el portal del paciente")
public record AccesoAlExpedienteDto(

        @Schema(description = "VISTA (se abrio en el portal) o PDF (se descargo)", example = "VISTA")
        String tipo,

        @Schema(description = "Correo con que inicio sesion quien lo abrio", example = "ana.perez@correo.sv")
        String correoDeSesion,

        LocalDateTime accedidoEn

) {
}
