package ues.edu.sv.education.model.dto.expediente;

import io.swagger.v3.oas.annotations.media.Schema;
import ues.edu.sv.education.model.dto.consulta.ClinicaResumenDto;
import ues.edu.sv.education.model.dto.consulta.ConsultaResponseDto;
import ues.edu.sv.education.model.dto.prescripcion.PrescripcionResponseDto;
import ues.edu.sv.education.model.dto.signosvitales.SignosVitalesResponseDto;
import ues.edu.sv.education.model.enums.TipoDeEventoDelExpediente;

import java.time.LocalDateTime;

/**
 * Un evento de la linea de tiempo del expediente (HU-15).
 *
 * La cabecera (tipo, fecha, estado, clinica, profesional) es lo que la linea
 * de tiempo necesita para ordenar, filtrar y decir quien y donde (criterio 3)
 * sin mirar dentro de cada tipo. El detalle va en el campo del tipo, y es la
 * MISMA respuesta que dan /consultas, /signos-vitales y /prescripciones: asi
 * la enfermera recibe aqui la consulta sin el diagnostico, igual que alla
 * (criterio 4), sin una segunda regla que pudiera desalinearse.
 */
@Schema(description = "Evento de la linea de tiempo: una consulta, una toma de signos vitales o una receta")
public record EventoDelExpedienteDto(
        TipoDeEventoDelExpediente tipo,
        @Schema(description = "Id del registro dentro de su tipo", example = "7") Long id,
        @Schema(description = "Fecha de la consulta, de la toma o de emision de la receta") LocalDateTime fecha,
        @Schema(description = "Estado del registro en su tipo: PENDIENTE, FINALIZADA, ACTIVA, EMITIDA o ANULADA",
                example = "FINALIZADA")
        String estado,
        @Schema(description = "Sede donde se registro; null si no se registro", nullable = true)
        ClinicaResumenDto clinica,
        ProfesionalDelEventoDto profesional,
        @Schema(description = "Solo si tipo = CONSULTA", nullable = true) ConsultaResponseDto consulta,
        @Schema(description = "Solo si tipo = SIGNOS_VITALES", nullable = true) SignosVitalesResponseDto signosVitales,
        @Schema(description = "Solo si tipo = RECETA", nullable = true) PrescripcionResponseDto receta
) {
}
