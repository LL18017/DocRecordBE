package ues.edu.sv.education.model.dto.portal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lo unico que el paciente cambia desde "Mi perfil" (DEF-07): si quiere el
 * recordatorio de cita. Boolean y no boolean para distinguir "no lo mando" de
 * false; el servicio responde 400 si falta. Cualquier otro campo del cuerpo
 * se ignora: nombre, correo y telefono los corrige el personal.
 */
@Schema(description = "Preferencias que el paciente puede cambiar desde su perfil")
public record PreferenciasDelPerfilRequest(
        @Schema(description = "true para recibir el recordatorio de cita 24 h antes", example = "false")
        Boolean recibeRecordatorios
) {
}
