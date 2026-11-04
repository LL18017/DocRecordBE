package ues.edu.sv.education.model.dto.anulacion;

import io.swagger.v3.oas.annotations.media.Schema;
import ues.edu.sv.education.model.dto.consulta.PacienteResumenDto;
import ues.edu.sv.education.model.enums.EstadoSolicitudDeAnulacion;
import ues.edu.sv.education.model.enums.TipoDocumentoAnulable;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Solicitud para anular una consulta o una receta fuera de la ventana de anulacion directa")
public record SolicitudDeAnulacionDto(

        @Schema(example = "4") Long solicitudId,

        TipoDocumentoAnulable tipo,

        @Schema(description = "La consulta anulada, o de la que salio la receta", example = "7")
        Long consultaId,

        @Schema(description = "Solo en las de tipo RECETA", example = "3")
        Long prescripcionId,

        PacienteResumenDto paciente,

        String motivo,

        @Schema(description = "Nombre de quien la pidio")
        String solicitadaPor,

        LocalDateTime solicitadaEn,

        @Schema(description = "Aprobaciones de administradores que necesita, fijadas al pedirla", example = "2")
        Integer aprobacionesRequeridas,

        List<AprobacionDto> aprobaciones,

        EstadoSolicitudDeAnulacion estado,

        LocalDateTime resueltaEn,

        @Schema(description = "Quien dio la ultima aprobacion o el rechazo; null si sigue pendiente o se cancelo")
        String resueltaPor,

        @Schema(description = "Solo en las RECHAZADA")
        String motivoRechazo,

        @Schema(description = "Si quien pregunta puede aprobarla o rechazarla ahora: es administrador, no la pidio "
                + "y no la aprobo ya")
        boolean puedeResolver

) {

    @Schema(description = "Visto bueno de un administrador")
    public record AprobacionDto(String administrador, LocalDateTime aprobadaEn) {
    }
}
