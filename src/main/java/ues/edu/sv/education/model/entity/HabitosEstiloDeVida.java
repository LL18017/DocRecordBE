package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import lombok.*;
import ues.edu.sv.education.model.enums.NivelHabito;

@Entity
@Table(name = "habitos_estilo_de_vida")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HabitosEstiloDeVida {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "id")
    private Long id;

    @Column(name = "descripcion", nullable = false, length = 255)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel", nullable = false, length = 10)
    private NivelHabito nivel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_habito_id", nullable = false)
    private TipoHabito tipoHabito;
}