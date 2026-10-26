package ues.edu.sv.education.model.dto.prescripcion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Lo que se pide para emitir una receta.
 *
 * NO lleva medicoId: quien firma es el medico autenticado, tomado del token.
 * La firma de una receta no es un dato que se elija en un formulario.
 *
 * La lista de medicamentos es @NotEmpty: una receta sin medicamentos no es una
 * receta, es un papel firmado. Ademas de esta validacion, el servicio vuelve a
 * comprobarlo (ver PrescripcionService), porque la regla es del dominio y no
 * del formulario.
 */
@Schema(description = "Datos para emitir una receta")
public record PrescripcionRequestDto(

        @Schema(description = "Consulta de la que sale la receta", example = "7")
        @NotNull(message = "La consulta es obligatoria")
        Long consultaId,

        @Schema(description = "Medicamentos indicados. No puede ir vacia")
        @NotEmpty(message = "Una receta debe llevar al menos un medicamento")
        @Valid
        List<MedicamentoRequestDto> medicamentos,

        // HU-24. Opcionales: una receta sin coincidencias de alergia no los
        // lleva. El servidor decide si hacen falta (no el cliente), porque la
        // alerta del formulario es una ayuda y no una garantia.
        @Schema(description = "Por que se receta pese a una alergia registrada. Obligatoria si algun "
                + "medicamento coincide con una alergia vigente del paciente", example = "Beneficio mayor al riesgo")
        @Size(max = 500, message = "La justificacion no puede pasar de 500 caracteres")
        String justificacionAlergia,

        @Schema(description = "Confirmacion explicita; obligatoria si la alergia coincidente es SEVERA")
        Boolean confirmaAlergiaSevera

) {
}
