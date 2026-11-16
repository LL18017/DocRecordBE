package ues.edu.sv.education.model.dto.portal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * "Mi perfil" del paciente (DEF-07, HU-33 c4): sus datos basicos de contacto y
 * su preferencia de recordatorios. Sin trazabilidad interna ni datos clinicos:
 * eso lo da /mi/expediente.
 */
@Schema(description = "Datos basicos del paciente de la sesion y su preferencia de recordatorios")
public record MiPerfilDto(
        Long pacienteId,
        String expediente,
        String nombres,
        String apellidos,
        @Schema(nullable = true) String correo,
        @Schema(nullable = true) String telefono,
        boolean recibeRecordatorios
) {
}
