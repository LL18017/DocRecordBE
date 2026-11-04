package ues.edu.sv.education.model.dto.medico;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Ficha del medico y estado de su acceso")
public record MedicoResponseDto(

        @Schema(example = "42") Long personaId,

        String nombres,

        String apellidos,

        String telefono,

        Long especialidadId,

        String especialidad,

        String registroJunta,

        @Schema(description = "false si se dio de baja la ficha: ya no atiende ni firma")
        boolean activo,

        @Schema(description = "Null si todavia no tiene cuenta para entrar")
        CuentaDto cuenta

) {

    @Schema(description = "La cuenta con que el medico entra al sistema")
    public record CuentaDto(

            Integer userId,

            String email,

            @Schema(description = "SIN_ACTIVAR mientras no canjee la invitacion; ACTIVA despues; "
                    + "DESACTIVADA si un administrador la apago")
            String estado,

            @Schema(description = "Si la cuenta tiene el rol MEDICO. Una baja de la ficha se lo quita")
            boolean conRolMedico,

            List<SedeDto> sedes

    ) {
    }

    public record SedeDto(Integer clinicaId, String nombre) {
    }
}
