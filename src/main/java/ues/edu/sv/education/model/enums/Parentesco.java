package ues.edu.sv.education.model.enums;

/**
 * Parentesco del familiar afectado por una condicion hereditaria (HU-13).
 *
 * Es la lista controlada del criterio 2, en el orden en que el expediente las
 * agrupa: primero los padres, luego los abuelos, luego los hermanos. Con el
 * parentesco escrito a mano no se podria responder "que pacientes tienen
 * diabetes en primer grado", que es lo que justifica registrarlo.
 */
public enum Parentesco {
    PADRE,
    MADRE,
    ABUELO,
    ABUELA,
    HERMANO,
    HERMANA,
    OTRO
}
