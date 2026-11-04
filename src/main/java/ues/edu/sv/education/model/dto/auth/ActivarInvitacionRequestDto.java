package ues.edu.sv.education.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * El canje de la invitacion: el token del correo y la contrasena que la
 * persona elige. Sin correo, por lo mismo que RestablecerContrasenaRequestDto:
 * a que cuenta corresponde lo dice el token.
 */
@Schema(description = "Token de la invitacion y contrasena elegida")
public record ActivarInvitacionRequestDto(

        @Schema(description = "Token que venia en el enlace del correo")
        @NotBlank(message = "El token no puede estar vacio")
        String token,

        @Schema(description = "Contrasena elegida, en texto plano. Se cifra antes de guardarla.",
                example = "MiClave2026!")
        @NotBlank(message = "La contrasena no puede estar vacia")
        @Pattern(regexp = ReglaDeContrasena.PATRON, message = ReglaDeContrasena.MENSAJE)
        String password

) {
}
