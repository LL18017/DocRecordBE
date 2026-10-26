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

        @Schema(description = "ACTIVO o INACTIVO. Un paciente inactivo conserva "
                + "todo su expediente; solo deja de aparecer en los listados de "
                + "trabajo diario.", example = "ACTIVO")
        String estado,

        @Schema(description = "Datos de la persona")
        PersonaResponseDto persona,

        @Schema(description = "Correo de quien registro al paciente. Null en pacientes anteriores a la trazabilidad.")
        String registradoPor,

        @Schema(description = "ID de la clinica donde se registro, si se indico")
        Integer clinicaRegistroId,

        @Schema(description = "Nombre de la clinica donde se registro, si se indico")
        String clinicaRegistro,

        @Schema(description = "Correo de quien lo modifico por ultima vez. Null si nunca se modifico.")
        String modificadoPor,

        @Schema(description = "Cuando se modifico por ultima vez. Null si nunca se modifico.")
        LocalDateTime modificadoEn

) {
}
