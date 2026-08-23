package ues.edu.sv.education.model.dto.consulta;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lo minimo para identificar al paciente dentro de una consulta.
 *
 * Deliberadamente NO trae DUI, telefono, direccion ni tipo de sangre: un
 * listado de consultas no necesita el expediente completo, y cada dato de
 * salud que viaja de mas es un dato que se puede filtrar. Quien necesite el
 * resto lo pide a /pacientes/{id}.
 */
@Schema(description = "Identificacion del paciente dentro de una consulta")
public record PacienteResumenDto(
        @Schema(example = "42") Long personaId,
        @Schema(example = "EXP-000042") String expediente,
        @Schema(example = "María Elena") String nombres,
        @Schema(example = "López Torres") String apellidos
) {
}
