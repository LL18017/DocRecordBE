package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "citas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cita_id")
    private Integer citaId;

    public static final String AGENDADA = "AGENDADA";
    public static final String CANCELADA = "CANCELADA";
    public static final String ATENDIDA = "ATENDIDA";

    /**
     * Paciente que tiene la cita: el del expediente, no una cuenta. La
     * mayoria de los pacientes no tiene usuario (ver V24).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_persona_id", nullable = false)
    private Paciente paciente;

    /**
     * Sede donde se atiende. Anulable: las citas anteriores a V24 no la
     * guardaron.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinica_id")
    private Clinicas clinica;

    /**
     * Fecha y hora programada para la cita.
     */
    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    /**
     * Tipo de cita.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_cita_id", nullable = false)
    private TipoCita tipo;

    /**
     * Médico asignado a la cita.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medico_user_id", nullable = false)
    private User medico;

    /**
     * AGENDADA, CANCELADA o ATENDIDA. Lo pone el servicio, no el cliente.
     */
    @Column(name = "estado", nullable = false, length = 30)
    private String estado;

    /**
     * Por que se cancelo. Una cita cancelada no se borra: conserva su estado
     * y su motivo (HU-32).
     */
    @Column(name = "motivo_cancelacion", length = 255)
    private String motivoCancelacion;
}