package ues.edu.sv.education.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import ues.edu.sv.education.model.dto.especialidad.EspecialidadResponseDto;

import java.util.List;

@Schema(description = "Cuenta de medico recien registrada")
public record RegistroMedicoResponseDto(

        @Schema(description = "ID del usuario", example = "7")
        Integer userId,

        @Schema(description = "Correo electronico", example = "juan.guerra@docrecord.sv")
        String email,

        @Schema(description = "Nombres de la persona", example = "Juan Armando")
        String nombres,

        @Schema(description = "Apellidos de la persona", example = "Guerra Guevara")
        String apellidos,

        @Schema(description = "Roles asignados", example = "[\"MEDICO\"]")
        List<String> roles,

        @Schema(description = "Especialidad medica")
        EspecialidadResponseDto especialidad,

        // La cuenta se crea aunque el correo de confirmacion no salga (ver
        // AuthService.registrarMedico). Este campo es lo unico que distingue
        // los dos casos desde el cliente: con true la persona debe revisar su
        // bandeja; con false NO le va a llegar nada y hay que decirselo, e
        // invitarla a reenviar el mismo formulario para reintentar el envio.
        @Schema(description = "true si el correo de confirmacion salio. Si es false la cuenta "
                + "existe pero el correo NO se envio: el cliente debe avisarlo y ofrecer reintentar "
                + "el registro con los mismos datos, que genera un token nuevo.",
                example = "true")
        boolean correoDeVerificacionEnviado

) {
}
