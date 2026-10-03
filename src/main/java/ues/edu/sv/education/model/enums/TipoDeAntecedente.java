package ues.edu.sv.education.model.enums;

/**
 * Tipo de un antecedente patologico (HU-12).
 *
 * Lista cerrada a proposito: la nota de diseno de HU-12 pide poder filtrar
 * despues por tipo, y con texto libre ("cirugía", "Cirugia", "operación")
 * eso seria imposible. La migracion V19 sostiene los mismos tres valores con
 * un CHECK.
 */
public enum TipoDeAntecedente {
    ENFERMEDAD,
    CIRUGIA,
    HOSPITALIZACION
}
