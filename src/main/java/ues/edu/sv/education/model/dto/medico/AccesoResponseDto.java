package ues.edu.sv.education.model.dto.medico;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado de darle acceso a un medico")
public record AccesoResponseDto(

        MedicoResponseDto medico,

        @Schema(description = "true si se mando una invitacion (cuenta nueva o sin activar)")
        boolean invitacionEnviada,

        @Schema(description = "false si se intento mandar la invitacion y el correo fallo: se puede reenviar")
        boolean correoEnviado

) {
}
