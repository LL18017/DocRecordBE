package ues.edu.sv.education.model.dto.prescripcion;

import io.swagger.v3.oas.annotations.media.Schema;
import ues.edu.sv.education.model.dto.consulta.ClinicaResumenDto;
import ues.edu.sv.education.model.dto.consulta.PacienteResumenDto;
import ues.edu.sv.education.model.enums.EstadoPrescripcion;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Receta emitida")
public record PrescripcionResponseDto(

        @Schema(example = "3") Long prescripcionId,

        @Schema(description = "Cuando se emitio la receta")
        LocalDateTime fecha,

        @Schema(description = "Consulta de la que salio", example = "7")
        Long consultaId,

        // HU-22, criterio 1: la receta hereda de la consulta paciente, medico,
        // clinica y fecha. Aqui viajan los cuatro para que quien lea la receta
        // no tenga que ir a pedir la consulta aparte.
        PacienteResumenDto paciente,

        MedicoFirmaDto medico,

        @Schema(description = "Clinica de la consulta de origen; null si la consulta no registro sucursal")
        ClinicaResumenDto clinica,

        @Schema(description = "Fecha y hora de la consulta de origen")
        LocalDateTime fechaConsulta,

        List<MedicamentoResponseDto> medicamentos,

        @Schema(description = "Justificacion escrita al recetar pese a una alergia (HU-24). Null si no hubo coincidencias")
        String justificacionAlergia,

        @Schema(description = "EMITIDA, o ANULADA (sigue visible, marcada). Una receta no se edita ni se borra")
        EstadoPrescripcion estado,

        @Schema(description = "Por que se anulo; null si esta EMITIDA")
        String motivoAnulacion,

        @Schema(description = "Cuando se anulo; null si esta EMITIDA")
        LocalDateTime anuladaEn,

        @Schema(description = "Nombre de quien la anulo; null si esta EMITIDA")
        String anuladaPor,

        @Schema(description = "Ultimo envio de la receta por correo (HU-25): PENDIENTE, ENVIADO, FALLIDO, "
                + "o SIN_CORREO si el paciente no tiene correo registrado y la receta solo se puede imprimir. "
                + "Null si la receta es anterior al envio automatico y nunca se mando.",
                example = "ENVIADO")
        String envioPorCorreo,

        @Schema(description = "Cuando se encolo el envio (PENDIENTE) o cuando termino (ENVIADO, FALLIDO)")
        LocalDateTime envioPorCorreoFecha

) {
}
