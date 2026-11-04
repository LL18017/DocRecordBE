package ues.edu.sv.education.model.dto.portal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Un termino tecnico del expediente con su explicacion en lenguaje llano (HU-34 c3)")
public record TerminoDelGlosarioDto(

        @Schema(description = "Clave estable con que la pantalla pide la explicacion. Los valores cerrados "
                + "van como <campo>.<VALOR DEL ENUM>", example = "severidad.SEVERA")
        String clave,

        @Schema(example = "Presión arterial")
        String termino,

        @Schema(example = "La fuerza con que la sangre empuja las paredes de las arterias...")
        String explicacion

) {
}
