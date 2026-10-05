package ues.edu.sv.education.model.dto.antecedente;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import ues.edu.sv.education.model.enums.EstadoDeAntecedente;
import ues.edu.sv.education.model.enums.TipoDeAntecedente;

import java.time.LocalDate;

/**
 * Alta de un antecedente patologico. No lleva medico: lo pone el servidor
 * desde el token, igual que en una consulta.
 */
public record AntecedentePatologicoRequestDto(
        @Schema(description = "persona_id del paciente", example = "12")
        @NotNull(message = "El paciente es obligatorio")
        Long pacienteId,

        @Schema(description = "ENFERMEDAD, CIRUGIA u HOSPITALIZACION", example = "CIRUGIA")
        @NotNull(message = "El tipo es obligatorio")
        TipoDeAntecedente tipo,

        @Schema(example = "Apendicectomía laparoscópica")
        @NotBlank(message = "La descripción no puede estar vacía")
        @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
        String descripcion,

        @Schema(description = "Fecha del antecedente, no la del registro", example = "2019-03-14")
        @NotNull(message = "La fecha es obligatoria")
        @PastOrPresent(message = "La fecha no puede ser futura")
        LocalDate fecha,

        @Schema(description = "ACTIVO o RESUELTO", example = "RESUELTO")
        @NotNull(message = "El estado es obligatorio")
        EstadoDeAntecedente estado
) {}
