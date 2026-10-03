package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una toma de constantes: el triage que precede a la consulta.
 *
 * Apunta a Paciente y a Enfermera, no a Persona, por lo mismo que Consulta
 * apunta a Paciente y Medico: la toma solo tiene sentido entre alguien
 * registrado como paciente y alguien registrado como personal de enfermeria.
 * La migracion V9 sostiene lo mismo con las claves foraneas.
 *
 * La consulta es opcional y la relacion se guarda aqui, no en Consulta: el
 * orden real de los hechos es que enfermeria toma las constantes ANTES de que
 * el medico abra la consulta. Modelarlo al reves obligaria a que existiera
 * primero la consulta, que es justo lo que no pasa.
 *
 * Los numeros son numeros. La maqueta del frontend guardaba "120/80 mmHg" y
 * "36.8°C" como texto; aqui la unidad vive en el nombre de la columna y el
 * valor es operable. Ver el comentario largo de V9.
 */
@Entity
@Table(name = "signos_vitales")
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class SignosVitales {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "signos_vitales_id")
    private Long signosVitalesId;

    @NotNull(message = "El paciente es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    // Sale del token, nunca del cuerpo de la peticion (ver
    // SignosVitalesService y EnfermeraAutenticado).
    @NotNull(message = "La enfermera es obligatoria")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enfermera_id", nullable = false)
    private Enfermera enfermera;

    // Null mientras la consulta no exista todavia, que es el caso normal.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consulta_id")
    private Consulta consulta;

    @NotNull
    @Column(name = "tomado_en", nullable = false)
    private LocalDateTime tomadoEn;

    // BigDecimal y no double: son NUMERIC en la base y un peso o una
    // temperatura no admiten el redondeo binario del coma flotante. 36.8 tiene
    // que volver a leerse 36.8.
    @Column(name = "peso_kg")
    private BigDecimal pesoKg;

    @Column(name = "estatura_cm")
    private BigDecimal estaturaCm;

    @Column(name = "temperatura_c")
    private BigDecimal temperaturaC;

    // Enteros envueltos (Short y no short) porque TODOS admiten null: una toma
    // parcial es lo normal. Un primitivo los guardaria como 0, que en una
    // saturacion o un pulso no significa "no se tomo" sino un paciente muerto.
    @Column(name = "presion_sistolica")
    private Short presionSistolica;

    @Column(name = "presion_diastolica")
    private Short presionDiastolica;

    @Column(name = "pulso_lpm")
    private Short pulsoLpm;

    @Column(name = "frecuencia_resp_rpm")
    private Short frecuenciaRespRpm;

    @Column(name = "saturacion_pct")
    private Short saturacionPct;

    @Column(name = "observaciones")
    private String observaciones;
}
