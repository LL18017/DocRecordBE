package ues.edu.sv.education.repository.proyeccion;

/**
 * Cuantas sedes tiene asignadas una cuenta.
 *
 * Mismo motivo que EspecialidadDePersona: el listado de usuarios necesita el
 * dato de toda la pagina, `User.clinicasAsignadas` es LAZY y getAll no corre
 * dentro de una transaccion. Contarlas en una sola consulta evita el N+1 y la
 * LazyInitializationException.
 */
public record SedesDeUsuario(Integer userId, Long sedes) {
}
