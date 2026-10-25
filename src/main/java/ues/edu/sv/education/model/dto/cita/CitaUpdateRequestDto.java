package ues.edu.sv.education.model.dto.cita;

/**
 * Reprogramar una cita o marcarla atendida. Todo es opcional: lo que llega
 * vacio no cambia. Cancelar no va por aqui sino por PATCH /citas/{id}/cancelacion,
 * que exige motivo.
 */
public record CitaUpdateRequestDto(

        String fechaHora,

        Integer tipoCitaId,

        Integer medicoId,

        /** Solo AGENDADA o ATENDIDA. */
        String estado

) {
}
