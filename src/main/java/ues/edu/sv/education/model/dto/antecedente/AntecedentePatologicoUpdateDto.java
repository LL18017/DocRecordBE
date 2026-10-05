package ues.edu.sv.education.model.dto.antecedente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import ues.edu.sv.education.model.enums.EstadoDeAntecedente;
import ues.edu.sv.education.model.enums.TipoDeAntecedente;

import java.time.LocalDate;

/**
 * Edicion de un antecedente. El paciente no se cambia: un antecedente
 * registrado en el expediente equivocado se elimina y se registra en el
 * correcto, no se muda de expediente.
 */
public record AntecedentePatologicoUpdateDto(
        @NotNull(message = "El tipo es obligatorio")
        TipoDeAntecedente tipo,

        @NotBlank(message = "La descripción no puede estar vacía")
        @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
        String descripcion,

        @NotNull(message = "La fecha es obligatoria")
        @PastOrPresent(message = "La fecha no puede ser futura")
        LocalDate fecha,

        @NotNull(message = "El estado es obligatorio")
        EstadoDeAntecedente estado
) {}
