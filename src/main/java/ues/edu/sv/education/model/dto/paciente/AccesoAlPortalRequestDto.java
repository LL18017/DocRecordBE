package ues.edu.sv.education.model.dto.paciente;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Darle al paciente su cuenta para el portal (HU-34).
 *
 * Solo hace falta el correo, y solo si el paciente todavia no tiene cuenta.
 * Si no viene, se usa el correo de contacto de su ficha. A diferencia del
 * medico no lleva sede: el paciente no opera en ninguna clinica, y una sede en
 * su cuenta lo mandaria a la pantalla de seleccion de sede, que no es para el.
 */
@Schema(description = "Correo de la cuenta del paciente")
public record AccesoAlPortalRequestDto(

        @Schema(description = "Opcional: sin el se usa el correo de contacto del paciente",
                example = "ana.perez@correo.sv")
        @Email(message = "Formato no valido para email")
        @Size(max = 100, message = "El email debe poseer menos de 100 caracteres")
        String email

) {
}
