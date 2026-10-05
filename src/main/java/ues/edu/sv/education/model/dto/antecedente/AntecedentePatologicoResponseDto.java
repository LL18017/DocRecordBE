package ues.edu.sv.education.model.dto.antecedente;

import ues.edu.sv.education.model.enums.EstadoDeAntecedente;
import ues.edu.sv.education.model.enums.TipoDeAntecedente;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un antecedente tal como lo lee el expediente. `registradoPor` es el nombre
 * del medico que lo registro.
 */
public record AntecedentePatologicoResponseDto(
        Long antecedenteId,
        Long pacienteId,
        TipoDeAntecedente tipo,
        String descripcion,
        LocalDate fecha,
        EstadoDeAntecedente estado,
        String registradoPor,
        LocalDateTime registradoEn
) {}
