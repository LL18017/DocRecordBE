package ues.edu.sv.education.model.dto.clinicas;

import jakarta.validation.groups.Default;

/**
 * Grupo de validacion del ALTA de una clinica (HU-26 criterio 1).
 *
 * ClinicasRequestDto sirve al alta y a la edicion, pero no exigen lo mismo: el
 * alta pide latitud y longitud, y la edicion deja completarlas despues porque
 * las clinicas registradas antes de este criterio no las tienen y su nombre o
 * telefono debe poder corregirse sin inventarles un punto. Extiende Default
 * para que en el alta sigan aplicando tambien todas las demas reglas.
 */
public interface AltaDeClinica extends Default {
}
