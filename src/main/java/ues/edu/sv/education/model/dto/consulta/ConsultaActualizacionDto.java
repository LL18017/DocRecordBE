package ues.edu.sv.education.model.dto.consulta;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Lo que se puede cambiar de una consulta ya registrada.
 *
 * Es un DTO aparte del de alta, y no el mismo con todo opcional, por lo que NO
 * contiene:
 *
 *  - pacienteId: cambiar el paciente de una consulta no es editarla, es mover
 *    un acto medico de un expediente a otro. Si un cliente lo manda se ignora,
 *    igual que se ignora el expediente en el alta de pacientes.
 *  - medicoId: quien atendio salio del token cuando se creo la consulta.
 *  - estado: se deriva del diagnostico (ver EstadoConsulta).
 *
 * Todo lo demas es opcional y sigue la regla de la casa: completar nunca
 * destruye. Un campo que llega null -o vacio- significa "no lo estoy
 * tocando", no "borralo". Un diagnostico no se borra por omision.
 */
@Schema(description = "Campos modificables de una consulta")
public record ConsultaActualizacionDto(

        @Schema(description = "Sucursal donde se atendio", example = "1")
        Integer clinicaId,

        @Schema(description = "Motivo de la consulta", example = "Dolor de garganta desde hace tres dias")
        String motivo,

        @Schema(description = "Diagnostico. Solo lo puede escribir un medico; a cualquier otro se le responde 403", example = "Faringitis aguda")
        String diagnostico,

        @Schema(description = "Fecha y hora de la atencion", example = "2026-08-23T10:30:00")
        LocalDateTime fecha

) {
}
