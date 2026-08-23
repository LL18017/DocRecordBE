package ues.edu.sv.education.model.dto.prescripcion;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Receta emitida")
public record PrescripcionResponseDto(

        @Schema(example = "3") Long prescripcionId,

        LocalDateTime fecha,

        @Schema(description = "Consulta de la que salio", example = "7")
        Long consultaId,

        MedicoFirmaDto medico,

        List<MedicamentoResponseDto> medicamentos

) {
}
