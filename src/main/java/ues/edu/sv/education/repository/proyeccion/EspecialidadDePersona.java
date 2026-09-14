package ues.edu.sv.education.repository.proyeccion;

/**
 * La especialidad de un medico, atada a su persona.
 *
 * Existe para que el listado de usuarios pueda mostrar la especialidad sin
 * caer en N+1. La especialidad no vive en `users` sino en `medicos`, y
 * `Medico.especialidad` es LAZY, asi que leerla usuario por usuario significa
 * una consulta por fila -- y ademas revienta con LazyInitializationException
 * fuera de una transaccion, que es justo donde corre UserService.getAll.
 */
public record EspecialidadDePersona(Long personaId, String especialidad) {
}
