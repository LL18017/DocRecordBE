package ues.edu.sv.education.service.signosvitales;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

/**
 * El indice de masa corporal y su clasificacion (HU-17).
 *
 * ── Por que una clase aparte y no dos lineas en el servicio ─────────────────
 * Porque el criterio 2 de la historia convierte esto en una regla clinica, no
 * en una division:
 *
 *   "dado un paciente menor de 18 anos, cuando se calcula el IMC, entonces el
 *    sistema indica que la clasificacion de adultos no aplica y debe
 *    interpretarse por percentiles"
 *
 * Y su nota de diseno explica por que: "la tabla de IMC de adultos aplicada a
 * un nino de 8 anos da una clasificacion sin sentido". No es un matiz
 * academico. Un nino de 8 anos con IMC 16 esta perfectamente sano, y la tabla
 * de adultos lo llamaria "bajo peso"; uno de 15 con IMC 25 seria "sobrepeso"
 * cuando puede estar en su percentil normal. Una etiqueta equivocada en un
 * expediente clinico orienta decisiones.
 *
 * El numero SI se calcula para menores -- es un dato objetivo -- y lo que
 * cambia es la clasificacion, que pasa a decir que hay que leerla por
 * percentiles.
 */
public final class IndiceDeMasaCorporal {

    private IndiceDeMasaCorporal() {
    }

    /** Lo que se le dice a quien mira el IMC de un menor de 18. */
    public static final String REQUIERE_PERCENTILES =
            "La clasificación de adultos no aplica en menores de 18 años; debe interpretarse por percentiles";

    /**
     * IMC = peso (kg) / talla (m)². Un decimal, como pide el criterio 1.
     *
     * Devuelve null si falta cualquiera de los dos: un IMC calculado con un
     * peso ausente no es un IMC aproximado, es un numero inventado.
     *
     * La estatura llega en CENTIMETROS (asi la guarda la columna desde V9) y se
     * convierte aqui. Dividir por la talla sin convertir da un IMC unas 10.000
     * veces menor, y es el error clasico de esta formula.
     */
    public static BigDecimal calcular(BigDecimal pesoKg, BigDecimal estaturaCm) {
        if (pesoKg == null || estaturaCm == null) return null;
        if (estaturaCm.signum() <= 0 || pesoKg.signum() <= 0) return null;

        BigDecimal metros = estaturaCm.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return pesoKg.divide(metros.multiply(metros), 1, RoundingMode.HALF_UP);
    }

    /**
     * La clasificacion de la OMS, o el aviso de que no aplica.
     *
     * `fechaNacimiento` puede ser null -- la columna lo admite -- y en ese caso
     * no se puede saber si es menor. Se devuelve el aviso de percentiles en vez
     * de clasificar: ante la duda, la etiqueta prudente es la que no afirma de
     * mas.
     */
    public static String clasificar(BigDecimal imc, LocalDate fechaNacimiento) {
        if (imc == null) return null;

        if (fechaNacimiento == null || esMenorDeEdad(fechaNacimiento)) {
            return REQUIERE_PERCENTILES;
        }

        double valor = imc.doubleValue();
        if (valor < 18.5) return "Bajo peso";
        if (valor < 25.0) return "Normal";
        if (valor < 30.0) return "Sobrepeso";
        if (valor < 35.0) return "Obesidad grado I";
        if (valor < 40.0) return "Obesidad grado II";
        return "Obesidad grado III";
    }

    private static boolean esMenorDeEdad(LocalDate fechaNacimiento) {
        return Period.between(fechaNacimiento, LocalDate.now()).getYears() < 18;
    }
}
