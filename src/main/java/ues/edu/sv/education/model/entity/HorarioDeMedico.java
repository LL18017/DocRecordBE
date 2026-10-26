package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

/**
 * Una franja semanal en la que un medico atiende en una sede (HU-29, V25).
 */
@Entity
@Table(name = "horarios_de_medico")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HorarioDeMedico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "horario_id")
    private Integer horarioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medico_user_id", nullable = false)
    private User medico;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinicas clinica;

    /** 1 = lunes ... 7 = domingo, como java.time.DayOfWeek. */
    @Column(name = "dia_semana", nullable = false)
    private Short diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;
}
