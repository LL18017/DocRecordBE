package ues.edu.sv.education.model.dto.signosvitales;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Datos de una toma de constantes.
 *
 * Sin `enfermeraId`: quien toma es siempre la enfermera autenticada (ver
 * SignosVitalesService), nunca un id que mande el cliente. Mismo motivo por el
 * que ClinicasRequestDto no lleva `userId` y PrescripcionRequestDto no lleva
 * `medicoId`.
 *
 * Los rangos repiten los CHECK de V9 a proposito. Sin ellos la base seguiria
 * rechazando el dato, pero el cliente recibiria un 500 de violacion de
 * restriccion en vez de un 400 que le dice que campo esta mal.
 */
@Schema(description = "Constantes tomadas a un paciente. Todas las medidas son opcionales: una toma parcial es lo normal")
public record SignosVitalesRequestDto(

        @Schema(description = "Paciente al que se le toman las constantes", example = "4")
        @NotNull(message = "El paciente es obligatorio")
        Long pacienteId,

        @Schema(
                description = "Consulta a la que precede, si ya existe. Normalmente se omite: "
                        + "enfermeria toma las constantes antes de que el medico abra la consulta",
                example = "12",
                nullable = true
        )
        Long consultaId,

        @Schema(
                description = "Momento de la toma. Si se omite, se usa el instante actual del servidor",
                example = "2026-09-13T08:15:00",
                nullable = true
        )
        LocalDateTime tomadoEn,

        @Schema(description = "Peso en kilogramos", example = "72.50", nullable = true)
        @DecimalMin(value = "0.1", message = "El peso debe ser mayor que cero")
        @DecimalMax(value = "500.0", message = "El peso no puede superar los 500 kg")
        BigDecimal pesoKg,

        @Schema(description = "Estatura en centimetros", example = "175.00", nullable = true)
        @DecimalMin(value = "0.1", message = "La estatura debe ser mayor que cero")
        @DecimalMax(value = "300.0", message = "La estatura no puede superar los 300 cm")
        BigDecimal estaturaCm,

        @Schema(description = "Temperatura en grados Celsius", example = "36.8", nullable = true)
        @DecimalMin(value = "25.0", message = "La temperatura debe estar entre 25 y 45 grados")
        @DecimalMax(value = "45.0", message = "La temperatura debe estar entre 25 y 45 grados")
        BigDecimal temperaturaC,

        @Schema(description = "Presion sistolica en mmHg (el numero de arriba)", example = "120", nullable = true)
        @Min(value = 40, message = "La presion sistolica debe estar entre 40 y 300 mmHg")
        @Max(value = 300, message = "La presion sistolica debe estar entre 40 y 300 mmHg")
        Short presionSistolica,

        @Schema(description = "Presion diastolica en mmHg (el numero de abajo)", example = "80", nullable = true)
        @Min(value = 20, message = "La presion diastolica debe estar entre 20 y 200 mmHg")
        @Max(value = 200, message = "La presion diastolica debe estar entre 20 y 200 mmHg")
        Short presionDiastolica,

        @Schema(description = "Pulso en latidos por minuto", example = "78", nullable = true)
        @Min(value = 20, message = "El pulso debe estar entre 20 y 300 lpm")
        @Max(value = 300, message = "El pulso debe estar entre 20 y 300 lpm")
        Short pulsoLpm,

        @Schema(description = "Frecuencia respiratoria en respiraciones por minuto", example = "16", nullable = true)
        @Min(value = 4, message = "La frecuencia respiratoria debe estar entre 4 y 90 rpm")
        @Max(value = 90, message = "La frecuencia respiratoria debe estar entre 4 y 90 rpm")
        Short frecuenciaRespRpm,

        @Schema(description = "Saturacion de oxigeno en porcentaje", example = "98", nullable = true)
        @Min(value = 50, message = "La saturacion debe estar entre 50 y 100 por ciento")
        @Max(value = 100, message = "La saturacion debe estar entre 50 y 100 por ciento")
        Short saturacionPct,

        @Schema(description = "Observaciones de enfermeria", nullable = true)
        @Size(max = 1000, message = "Las observaciones no pueden superar los 1000 caracteres")
        String observaciones

) {
}
