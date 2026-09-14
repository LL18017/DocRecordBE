package ues.edu.sv.education.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * El canje del enlace: el token que llego por correo y la contrasena nueva.
 *
 * El correo de la cuenta NO viaja aqui y no es un olvido. A quien se le cambia
 * la contrasena lo decide el token, que es lo unico que prueba algo; aceptar
 * ademas un correo en el cuerpo abriria la puerta a canjear un token propio
 * contra una cuenta ajena si alguna vez las dos cosas se leyeran por separado.
 * Es la misma regla que en el resto del sistema -- la identidad sale de la
 * credencial, nunca del cuerpo de la peticion.
 */
@Schema(description = "Token del enlace de recuperacion y contrasena nueva")
public record RestablecerContrasenaRequestDto(

        @Schema(description = "Token que venia en el enlace del correo")
        @NotBlank(message = "El token no puede estar vacio")
        String token,

        @Schema(description = "Contrasena nueva en texto plano. Se cifra antes de guardarla y "
                + "nunca se registra ni se devuelve.", example = "NuevaClave2026!")
        @NotBlank(message = "La contrasena no puede estar vacia")
        String password

) {
}
