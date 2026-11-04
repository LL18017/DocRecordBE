package ues.edu.sv.education.model.dto.medico;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Darle acceso al sistema a un medico que ya tiene ficha.
 *
 * Sin cuenta, hace falta el correo: se crea la cuenta y se manda la
 * invitacion. Con cuenta, el correo sobra. La sede es obligatoria mientras la
 * cuenta no tenga ninguna: un medico sin sede inicia sesion y se queda en la
 * pantalla de seleccion de clinica.
 */
@Schema(description = "Correo de la cuenta nueva y primera sede")
public record AccesoRequestDto(

        @Schema(description = "Obligatorio si el medico todavia no tiene cuenta", example = "lucia.mejia@docrecord.sv")
        @Email(message = "Formato no valido para email")
        @Size(max = 100, message = "El email debe poseer menos de 100 caracteres")
        String email,

        @Schema(description = "Primera sede donde va a trabajar; obligatoria si la cuenta no tiene ninguna",
                example = "3")
        Integer clinicaId

) {
}
