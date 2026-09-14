package ues.edu.sv.education.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lo que se envia para pedir el enlace de recuperacion (HU-04).
 *
 * Solo el correo. No lleva ningun otro dato a proposito: cualquier campo
 * adicional -el nombre, el DUI, "confirma tu especialidad"- convertiria el
 * formulario en un oraculo para averiguar datos de cuentas ajenas.
 */
@Schema(description = "Correo de la cuenta cuya contrasena se quiere recuperar")
public record RecuperacionRequestDto(

        @Schema(description = "Correo con el que esta registrada la cuenta",
                example = "juan.guerra@docrecord.sv")
        @NotBlank(message = "El correo no puede estar vacio")
        @Email(message = "Formato no valido para email")
        @Size(max = 100, message = "El email debe poseer menos de 100 caracteres")
        String email

) {
}
