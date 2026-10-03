package ues.edu.sv.education.model.dto.signosvitales;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * La enfermera responsable de la toma.
 *
 * Mismo criterio que MedicoFirmaDto: lo que respalda una constante es quien la
 * tomo, no su registro de junta ni si sigue activa. El expediente que el
 * medico lee necesita un nombre, no una ficha de personal.
 */
@Schema(description = "Personal de enfermeria que registro la toma")
public record EnfermeraTomaDto(
        @Schema(example = "9") Long personaId,
        @Schema(example = "María Elena") String nombres,
        @Schema(example = "López Torres") String apellidos
) {
}
