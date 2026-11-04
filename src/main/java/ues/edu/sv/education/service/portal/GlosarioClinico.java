package ues.edu.sv.education.service.portal;

import ues.edu.sv.education.model.dto.portal.TerminoDelGlosarioDto;

import java.util.List;

/**
 * Los terminos tecnicos del expediente explicados en lenguaje llano (HU-34
 * criterio 3).
 *
 * ── Por que vive en el backend ────────────────────────────────────────────
 * Porque el expediente se lee por dos caminos -la pantalla del portal y el
 * PDF que se descarga- y los dos tienen que explicar lo mismo. Con el glosario
 * en el frontend, el PDF necesitaria su propia copia, y dos copias de un texto
 * escrito a mano terminan diciendo cosas distintas. Aqui hay una sola: viaja
 * en GET /mi/expediente (la pantalla la toma de ahi) y se imprime al final del
 * PDF.
 *
 * ── Que es una clave ──────────────────────────────────────────────────────
 * El nombre estable con que la pantalla pide la explicacion de un campo
 * ("presionArterial") o de un valor cerrado ("severidad.SEVERA",
 * "consulta.ANULADA"). Los valores cerrados se escriben con el nombre exacto
 * del enum, para que la pantalla pueda armar la clave con el dato que recibe.
 * Cambiar una clave rompe la pantalla en silencio: se agregan, no se renombran.
 *
 * ── Que NO explica ────────────────────────────────────────────────────────
 * El texto libre que escribio el medico (el diagnostico en si, el nombre del
 * medicamento). Eso no tiene una explicacion fija; lo que si se explica es que
 * significa cada campo, para que el paciente sepa que esta leyendo.
 *
 * Las explicaciones son orientativas, no consejo medico: dicen que mide o que
 * significa cada cosa, no si el valor del paciente esta bien o mal. Eso lo
 * interpreta su medico, y el portal lo dice.
 */
public final class GlosarioClinico {

    private GlosarioClinico() {
    }

    /** En el orden en que aparecen los terminos en el expediente. */
    public static final List<TerminoDelGlosarioDto> TERMINOS = List.of(
            // ── Datos personales ──────────────────────────────────────────
            t("expediente", "Número de expediente",
                    "El número con que la clínica identifica tu historia clínica. Tenlo a mano cuando pidas una cita."),
            t("tipoSangre", "Tipo de sangre",
                    "Tu grupo sanguíneo (A, B, AB u O) y su factor Rh (+ o −). Es importante si alguna vez necesitas recibir sangre."),

            // ── Alergias ──────────────────────────────────────────────────
            t("alergia", "Alergia",
                    "Una reacción de tu cuerpo ante una sustancia (un medicamento, un alimento, un material) que a otras personas no les hace daño."),
            t("reaccion", "Reacción",
                    "Lo que te ocurrió al tener contacto con la sustancia: por ejemplo ronchas, hinchazón o falta de aire."),
            t("severidad.LEVE", "Severidad leve",
                    "Una reacción molesta pero pasajera, que no pone en riesgo tu salud."),
            t("severidad.MODERADA", "Severidad moderada",
                    "Una reacción que necesitó o puede necesitar atención médica."),
            t("severidad.SEVERA", "Severidad severa",
                    "Una reacción que puede poner en riesgo tu vida. Avísala siempre antes de que te receten o te apliquen algo."),

            // ── Signos vitales ────────────────────────────────────────────
            t("signosVitales", "Signos vitales",
                    "Las medidas básicas de cómo está funcionando tu cuerpo, que enfermería toma antes de la consulta."),
            t("presionArterial", "Presión arterial",
                    "La fuerza con que la sangre empuja las paredes de las arterias. Se anota con dos números en mmHg (milímetros de mercurio): "
                            + "el primero (sistólica) cuando el corazón late y el segundo (diastólica) cuando descansa entre latidos."),
            t("frecuenciaCardiaca", "Frecuencia cardiaca (pulso)",
                    "Cuántas veces late tu corazón en un minuto. Se anota en lpm (latidos por minuto)."),
            t("frecuenciaRespiratoria", "Frecuencia respiratoria",
                    "Cuántas veces respiras en un minuto. Se anota en rpm (respiraciones por minuto)."),
            t("temperatura", "Temperatura",
                    "La temperatura de tu cuerpo en grados Celsius (°C). Alrededor de 37 °C es lo habitual; desde 38 °C se considera fiebre."),
            t("saturacion", "Saturación de oxígeno",
                    "Qué porcentaje de tu sangre va cargado de oxígeno. Se mide con una pinza en el dedo llamada oxímetro."),
            t("imc", "Índice de masa corporal (IMC)",
                    "Un número que relaciona tu peso con tu estatura (peso en kg dividido entre la estatura en metros al cuadrado). "
                            + "Sirve para estimar si tu peso es adecuado para tu talla; en menores de 18 años se interpreta con tablas de crecimiento."),

            // ── Consultas ─────────────────────────────────────────────────
            t("motivo", "Motivo de consulta",
                    "Por qué fuiste a consulta, tal como se lo contaste al médico."),
            t("diagnostico", "Diagnóstico",
                    "Lo que el médico concluyó sobre tu estado de salud después de escucharte y revisarte."),
            t("exploracionFisica", "Exploración física",
                    "Lo que el médico observó al revisarte: por ejemplo escuchar tu pecho, revisar tu garganta o tomarte el pulso."),
            t("indicaciones", "Indicaciones",
                    "Las recomendaciones y cuidados que te dejó el médico para después de la consulta."),
            t("consulta.PENDIENTE", "Consulta pendiente",
                    "La consulta quedó registrada, pero el médico todavía no anotó el diagnóstico."),
            t("consulta.FINALIZADA", "Consulta finalizada",
                    "La consulta se cerró con un diagnóstico."),
            t("consulta.ANULADA", "Consulta anulada",
                    "La consulta se invalidó, por ejemplo porque se registró por error. Se conserva en tu historial con el motivo, "
                            + "pero no cuenta como una atención válida."),

            // ── Recetas ───────────────────────────────────────────────────
            t("receta", "Receta (prescripción)",
                    "La indicación escrita y firmada por el médico de los medicamentos que debes tomar y cómo tomarlos."),
            t("dosis", "Dosis",
                    "Cuánto medicamento tomar cada vez, por ejemplo 500 mg o una tableta."),
            t("viaAdministracion", "Vía de administración",
                    "Por dónde entra el medicamento a tu cuerpo: oral (por la boca), tópica (sobre la piel), "
                            + "intramuscular (inyectado en el músculo) o intravenosa (por la vena), entre otras."),
            t("frecuencia", "Frecuencia",
                    "Cada cuánto tomar el medicamento, por ejemplo cada 8 horas."),
            t("duracion", "Duración",
                    "Durante cuánto tiempo seguir el tratamiento. No lo suspendas antes sin consultarlo con tu médico."),
            t("receta.EMITIDA", "Receta vigente",
                    "La receta es válida: es la indicación que debes seguir."),
            t("receta.ANULADA", "Receta anulada",
                    "La receta se invalidó y ya no debe usarse para comprar ni para tomar el medicamento. Se conserva con el motivo."),

            // ── Citas ─────────────────────────────────────────────────────
            t("cita.AGENDADA", "Cita agendada",
                    "La cita está reservada para esa fecha y hora. Si no puedes asistir, avisa a la clínica con anticipación.")
    );

    private static TerminoDelGlosarioDto t(String clave, String termino, String explicacion) {
        return new TerminoDelGlosarioDto(clave, termino, explicacion);
    }
}
