package ues.edu.sv.education.model.dto.paciente;

import io.swagger.v3.oas.annotations.media.Schema;
import ues.edu.sv.education.model.dto.persona.PersonaResponseDto;

import java.time.LocalDateTime;

@Schema(description = "Paciente registrado")
public record PacienteResponseDto(

        @Schema(description = "ID de la persona (tambien PK de paciente, no hay pacienteId separado)", example = "42")
        Long personaId,

        @Schema(description = "Numero de expediente", example = "P-000001")
        String expediente,

        @Schema(description = "Tipo de sangre, puede ser null")
        String tipoSangre,

        @Schema(description = "Fecha de alta como paciente")
        LocalDateTime creadoEn,

        @Schema(description = "Datos de la persona")
        PersonaResponseDto persona

) {
}
