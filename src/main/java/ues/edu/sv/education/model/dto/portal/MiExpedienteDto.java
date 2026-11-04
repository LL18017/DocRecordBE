package ues.edu.sv.education.model.dto.portal;

import io.swagger.v3.oas.annotations.media.Schema;
import ues.edu.sv.education.model.dto.Alergia.AlergiaResponse;
import ues.edu.sv.education.model.dto.cita.CitaResponseDto;
import ues.edu.sv.education.model.dto.consulta.ConsultaResponseDto;
import ues.edu.sv.education.model.dto.prescripcion.PrescripcionResponseDto;
import ues.edu.sv.education.model.dto.signosvitales.SignosVitalesResponseDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * El expediente tal como lo ve su dueño en el portal (HU-34).
 *
 * Una sola respuesta y no un endpoint por seccion por dos razones. La primera
 * es el criterio 6: "abrir el expediente" es UN acceso y debe quedar como una
 * sola fila en la bitacora, no como cuatro filas que habria que reagrupar para
 * saber cuantas veces lo abrio. La segunda es que el PDF necesita exactamente
 * lo mismo, y armarlo desde un solo lugar garantiza que la pantalla y el
 * papel dicen lo mismo.
 *
 * Las secciones reutilizan los DTOs que ya lee el personal clinico; lo unico
 * propio es `paciente`, que deja fuera la trazabilidad interna de
 * PacienteResponseDto (los correos del personal que lo registro y lo
 * modifico), que no es informacion del paciente sino de la clinica.
 */
@Schema(description = "El expediente del paciente de la sesion, en solo lectura")
public record MiExpedienteDto(

        DatosDelPacienteDto paciente,

        @Schema(description = "Alergias vigentes, las SEVERAS primero")
        List<AlergiaResponse> alergias,

        @Schema(description = "Ultima toma de signos vitales valida; null si nunca se le tomaron")
        SignosVitalesResponseDto ultimosSignosVitales,

        @Schema(description = "Consultas, la mas reciente primero. Las ANULADAS vienen marcadas con su motivo.")
        List<ConsultaResponseDto> consultas,

        @Schema(description = "Recetas, la mas reciente primero. Las ANULADAS vienen marcadas con su motivo.")
        List<PrescripcionResponseDto> recetas,

        @Schema(description = "Citas AGENDADAS a futuro, la mas proxima primero")
        List<CitaResponseDto> proximasCitas,

        @Schema(description = "Explicacion en lenguaje llano de los terminos tecnicos (HU-34 c3)")
        List<TerminoDelGlosarioDto> glosario,

        @Schema(description = "Cuando se armo esta respuesta (hora del servidor)")
        LocalDateTime consultadoEn

) {

    @Schema(description = "Datos personales del paciente")
    public record DatosDelPacienteDto(
            Long personaId,
            String expediente,
            String nombres,
            String apellidos,
            String dui,
            LocalDate fechaNacimiento,
            String sexo,
            String tipoSangre,
            String telefono,
            String direccion,
            String email
    ) {
    }
}
