package ues.edu.sv.education.model.enums;

/**
 * Los documentos clinicos que se anulan con ventana y aprobacion (V35).
 *
 * Cada uno tiene su fila en parametros_de_anulacion: la clinica puede querer
 * mas cuidado con una receta, que ya se despacho, que con una consulta.
 */
public enum TipoDocumentoAnulable {
    CONSULTA,
    RECETA
}
