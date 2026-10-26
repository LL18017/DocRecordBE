package ues.edu.sv.education.model.dto.consulta;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lo que se puede cambiar de una consulta ya registrada.
 *
 * Es un DTO aparte del de alta, y no el mismo con todo opcional, por lo que NO
 * contiene:
 *
 *  - pacienteId: cambiar el paciente de una consulta no es editarla, es mover
 *    un acto medico de un expediente a otro.
 *  - medicoId, clinicaId y fecha (HU-19, criterio 3): son la FIRMA de la
 *    consulta. Quien atendio, donde y cuando quedan fijados al registrarla y
 *    no se editan; si un cliente los manda se ignoran, igual que pacienteId.
 *  - estado: se deriva del diagnostico (ver EstadoConsulta).
 *
 * Todo lo demas es opcional y sigue la regla de la casa: completar nunca
 * destruye. Un campo que llega null -o vacio- significa "no lo estoy
 * tocando", no "borralo". Un diagnostico no se borra por omision.
 */
@Schema(description = "Campos modificables de una consulta")
public record ConsultaActualizacionDto(

        @Schema(description = "Motivo de la consulta", example = "Dolor de garganta desde hace tres dias")
        String motivo,

        @Schema(description = "Diagnostico. Solo lo puede escribir un medico; a cualquier otro se le responde 403", example = "Faringitis aguda")
        String diagnostico,

        @Schema(description = "Exploracion fisica", example = "Faringe eritematosa, sin exudado")
        String exploracionFisica,

        @Schema(description = "Indicaciones para el paciente", example = "Reposo e hidratacion")
        String indicaciones

) {
}
