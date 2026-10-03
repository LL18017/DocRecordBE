package ues.edu.sv.education.model.dto.consulta;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Lo que se puede pedir al crear o actualizar una consulta.
 *
 * NO lleva medicoId. La identidad del medico sale del token (ver
 * ConsultaService.medicoAutenticado): aceptarla en el cuerpo dejaria que
 * cualquiera firmara consultas a nombre de otro, que es exactamente el fallo
 * que ya se corrigio en ClinicaService.
 *
 * NO lleva estado. El estado se deriva del diagnostico; ver EstadoConsulta.
 */
@Schema(description = "Datos para registrar o actualizar una consulta medica")
public record ConsultaRequestDto(

        @Schema(description = "Paciente atendido (persona_id del paciente)", example = "42")
        @NotNull(message = "El paciente es obligatorio")
        Long pacienteId,

        @Schema(description = "Sucursal donde se atendio. Opcional: un medico sin sucursales registradas atiende igual", example = "1")
        Integer clinicaId,

        @Schema(description = "Motivo de la consulta, en palabras del paciente", example = "Dolor de garganta desde hace tres dias")
        String motivo,

        @Schema(description = "Diagnostico. Solo lo puede escribir un medico; a cualquier otro se le responde 403", example = "Faringitis aguda")
        String diagnostico,

        @Schema(description = "Fecha y hora de la atencion. Si no viene, la pone el servidor", example = "2026-08-23T10:30:00")
        LocalDateTime fecha

) {
}
