package ues.edu.sv.education.model.dto.Alergia;

import ues.edu.sv.education.model.enums.SeveridadDeAlergia;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Una alergia tal como la lee el expediente. `registradaPor` y `eliminadaPor`
 * son nombres de persona, no ids: es lo que la pantalla muestra.
 *
 * Los dos campos de baja solo vienen llenos al pedir por id una alergia ya
 * eliminada; el listado del paciente nunca las incluye.
 */
public record AlergiaResponse(
        Integer alergiaId,
        Long pacienteId,
        String sustancia,
        String reaccion,
        SeveridadDeAlergia severidad,
        LocalDate fechaDeteccion,
        String registradaPor,
        LocalDateTime registradaEn,
        String eliminadaPor,
        LocalDateTime eliminadaEn
) {}
