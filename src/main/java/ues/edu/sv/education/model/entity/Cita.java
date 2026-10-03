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

    /**
     * Paciente que tiene la cita.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_user_id", nullable = false)
    private User paciente;

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
     * Estado actual de la cita.
     */
    @Column(name = "estado", nullable = false, length = 30)
    private String estado;
}