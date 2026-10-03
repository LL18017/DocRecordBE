package ues.edu.sv.education.model.dto.User;

import io.swagger.v3.oas.annotations.media.Schema;
import ues.edu.sv.education.model.dto.roles.RoleDto;

import java.util.List;

@Schema(description = "Cuenta creada por un administrador via POST /user")
public record AltaUsuarioResponseDto(

        @Schema(description = "ID del usuario", example = "12")
        Integer userId,

        @Schema(description = "Correo electronico", example = "enfermera.turno@docrecord.sv")
        String email,

        @Schema(description = "Nombre completo de la persona", example = "Ana Torres")
        String userName,

        @Schema(description = "Roles ya asignados. Nace vacio: se asigna con POST /user/{id}/role/{id}")
        List<RoleDto> roles,

        // La cuenta se crea aunque el correo de confirmacion no salga -mismo
        // criterio que RegistroMedicoResponseDto.correoDeVerificacionEnviado-.
        // Este campo es lo unico que distingue los dos casos desde el cliente.
        @Schema(description = "true si el correo de confirmacion salio. Si es false la cuenta existe "
                + "pero deshabilitada y sin aviso enviado: un administrador puede asignarle una "
                + "contrasena por POST /user/{id}/password, que tambien la habilita, en vez de "
                + "depender de que el correo llegue.",
                example = "true")
        boolean correoDeVerificacionEnviado

) {
}
