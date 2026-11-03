package ues.edu.sv.education.model.enums;

/**
 * En que va una solicitud de anulacion (V35).
 *
 * PENDIENTE hasta juntar las aprobaciones o recibir un rechazo. CANCELADA
 * cuando el documento se anulo por otro camino mientras esperaba: la receta
 * de una consulta que se anulo entera, por ejemplo. Ninguna resuelta vuelve a
 * PENDIENTE; para insistir se pide otra.
 */
public enum EstadoSolicitudDeAnulacion {
    PENDIENTE,
    APROBADA,
    RECHAZADA,
    CANCELADA
}
