package ues.edu.sv.education.model.dto.Alergia;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import ues.edu.sv.education.model.enums.SeveridadDeAlergia;

import java.time.LocalDate;

/**
 * Alta de una alergia. No lleva quien la registra: lo pone el servidor desde
 * el token, igual que en un antecedente o una consulta.
 */
public record AlergiaRequest(
        @Schema(description = "persona_id del paciente", example = "12")
        @NotNull(message = "El paciente es obligatorio")
        Long pacienteId,

        @Schema(example = "Penicilina")
        @NotBlank(message = "La sustancia no puede estar vacía")
        @Size(max = 100, message = "La sustancia no puede superar los 100 caracteres")
        String sustancia,

        @Schema(description = "Tipo de reacción que provoca", example = "Urticaria generalizada")
        @NotBlank(message = "La reacción no puede estar vacía")
        @Size(max = 255, message = "La reacción no puede superar los 255 caracteres")
        String reaccion,

        @Schema(description = "LEVE, MODERADA o SEVERA", example = "SEVERA")
        @NotNull(message = "La severidad es obligatoria")
        SeveridadDeAlergia severidad,

        @Schema(description = "Fecha en que se detectó, no la del registro", example = "2021-07-02")
        @NotNull(message = "La fecha de detección es obligatoria")
        @PastOrPresent(message = "La fecha de detección no puede ser futura")
        LocalDate fechaDeteccion
) {}
