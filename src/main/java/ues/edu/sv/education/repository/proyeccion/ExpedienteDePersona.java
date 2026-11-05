package ues.edu.sv.education.repository.proyeccion;

/**
 * El numero de expediente de una persona que ademas es paciente.
 *
 * Mismo motivo que EspecialidadDePersona: el listado de usuarios lo muestra
 * para toda la pagina en una sola consulta, sin cargar la entidad Paciente
 * (que trae quien la registro y en que sede) fila por fila.
 */
public record ExpedienteDePersona(Long personaId, String expediente) {
}
