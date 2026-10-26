package ues.edu.sv.education.model.enums;

/**
 * Estado de una receta (HU-22, criterio 3).
 *
 * Una receta entregada no se edita ni se borra: se ANULA, y la anulacion deja
 * rastro (motivo, quien, cuando). Es el unico cambio de estado que existe;
 * una anulada no vuelve a EMITIDA, para corregirla se emite una nueva.
 */
public enum EstadoPrescripcion {
    EMITIDA,
    ANULADA
}
