package ues.edu.sv.education.model.dto.consulta;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Lo que se pide para registrar una consulta.
 *
 * NO lleva medicoId. La identidad del medico sale del token (ver
 * ConsultaService.medicoAutenticado): aceptarla en el cuerpo dejaria que
 * cualquiera firmara consultas a nombre de otro, que es exactamente el fallo
 * que ya se corrigio en ClinicaService.
 *
 * NO lleva estado. El estado se deriva del diagnostico; ver EstadoConsulta.
 *
 * HU-19, criterio 1: motivo y diagnostico son obligatorios (los comprueba
 * ConsultaService, no una anotacion: con @NotBlank una enfermera que mande un
 * formulario incompleto recibiria 400 en vez del 403 que le corresponde, porque
 * la validacion del cuerpo corre antes que la autorizacion); la exploracion
 * fisica y las indicaciones no. Quien registra una consulta es siempre un
 * medico (el alta esta cerrada a enfermeria), asi que exigir el diagnostico
 * aqui no le pide a nadie algo que no le corresponda escribir.
 */
@Schema(description = "Datos para registrar una consulta medica")
public record ConsultaRequestDto(

        @Schema(description = "Paciente atendido (persona_id del paciente)", example = "42")
        @NotNull(message = "El paciente es obligatorio")
        Long pacienteId,

        @Schema(description = "Sucursal donde se atendio. Opcional: un medico sin sucursales registradas atiende igual", example = "1")
        Integer clinicaId,

        @Schema(description = "Motivo de la consulta, en palabras del paciente. Obligatorio", example = "Dolor de garganta desde hace tres dias")
        String motivo,

        @Schema(description = "Diagnostico. Obligatorio; solo lo puede escribir un medico (403 a cualquier otro)", example = "Faringitis aguda")
        String diagnostico,

        @Schema(description = "Exploracion fisica. Opcional", example = "Faringe eritematosa, sin exudado")
        String exploracionFisica,

        @Schema(description = "Indicaciones para el paciente. Opcional", example = "Reposo e hidratacion")
        String indicaciones,

        @Schema(description = "Fecha y hora de la atencion. Si no viene, la pone el servidor", example = "2026-08-23T10:30:00")
        LocalDateTime fecha

) {
}
