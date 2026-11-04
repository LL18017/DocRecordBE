package ues.edu.sv.education.model.dto.paciente;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Como esta el acceso del paciente a su portal (HU-34), y que paso al darlo.
 *
 * Lo devuelven tanto la consulta del estado como el alta del acceso, para que
 * la pantalla del expediente tenga una sola forma que pintar.
 */
@Schema(description = "Acceso del paciente al portal")
public record AccesoAlPortalDto(

        @Schema(description = "persona_id del paciente", example = "42")
        Long pacienteId,

        @Schema(description = "Correo de contacto de la ficha del paciente, para proponerlo como usuario; puede ser null")
        String correoDeContacto,

        @Schema(description = "La cuenta del paciente; null si todavia no tiene")
        CuentaDto cuenta,

        @Schema(description = "true si en esta llamada se mando una invitacion (cuenta nueva o sin activar)")
        boolean invitacionEnviada,

        @Schema(description = "false si se intento mandar la invitacion y el correo fallo: se puede reenviar")
        boolean correoEnviado

) {

    public record CuentaDto(
            Integer userId,
            String email,
            @Schema(description = "SIN_ACTIVAR, ACTIVA o DESACTIVADA", example = "SIN_ACTIVAR")
            String estado,
            @Schema(description = "Si la cuenta tiene el rol PACIENTE, que es el que abre el portal")
            boolean conRolPaciente
    ) {
    }
}
