package ues.edu.sv.education.model.dto.consulta;

import io.swagger.v3.oas.annotations.media.Schema;
import ues.edu.sv.education.model.enums.EstadoConsulta;

import java.time.LocalDateTime;

@Schema(description = "Consulta medica registrada")
public record ConsultaResponseDto(

        @Schema(example = "7") Long consultaId,

        LocalDateTime fecha,

        String motivo,

        String diagnostico,

        @Schema(description = "Exploracion fisica; null si no se registro")
        String exploracionFisica,

        @Schema(description = "Indicaciones para el paciente; null si no se registraron")
        String indicaciones,

        @Schema(description = "PENDIENTE mientras no haya diagnostico; FINALIZADA cuando lo hay")
        EstadoConsulta estado,

        PacienteResumenDto paciente,

        MedicoResumenDto medico,

        @Schema(description = "Null si la consulta no registro sucursal")
        ClinicaResumenDto clinica

) {
}
