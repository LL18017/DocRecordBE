package ues.edu.sv.education.model.dto.prescripcion;

import io.swagger.v3.oas.annotations.media.Schema;
import ues.edu.sv.education.model.dto.consulta.PacienteResumenDto;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Receta emitida")
public record PrescripcionResponseDto(

        @Schema(example = "3") Long prescripcionId,

        LocalDateTime fecha,

        @Schema(description = "Consulta de la que salio", example = "7")
        Long consultaId,

        // Campo aditivo: antes esta respuesta no decia de que PACIENTE era la
        // receta, lo que hacia imposible listar "el historico de este
        // paciente" sin ir a consultar la consulta aparte. Se reutiliza
        // PacienteResumenDto -el mismo tipo que ya usa ConsultaResponseDto-
        // en vez de duplicar un DTO con la misma forma (personaId,
        // expediente, nombres, apellidos).
        PacienteResumenDto paciente,

        MedicoFirmaDto medico,

        List<MedicamentoResponseDto> medicamentos

) {
}
