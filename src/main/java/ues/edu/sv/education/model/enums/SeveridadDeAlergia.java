package ues.edu.sv.education.model.enums;

/**
 * Que tan grave es la reaccion de un paciente a una sustancia (HU-11).
 *
 * Lista cerrada a proposito: la ficha destaca las SEVERAS arriba con color de
 * advertencia (criterio 2) y HU-24 alertara al prescribir. Con la severidad
 * escrita a mano -"alta", "grave", "fuerte"- ninguna de las dos cosas podria
 * saber cuales son.
 *
 * El orden de declaracion es de menor a mayor; el listado las invierte para
 * poner las severas primero (ver AlergiaService).
 */
public enum SeveridadDeAlergia {
    LEVE,
    MODERADA,
    SEVERA
}
