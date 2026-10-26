package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import lombok.*;
import ues.edu.sv.education.model.enums.NivelHabito;

import java.time.LocalDateTime;

/**
 * Una version ANTERIOR de un habito (HU-14, criterio 2): lo que decia antes
 * de que alguien lo editara, con quien y cuando la escribio, y quien y cuando
 * la reemplazo. Solo se inserta; nunca se modifica.
 */
@Entity
@Table(name = "habitos_estilo_de_vida_historial")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HabitoHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "historial_id")
    private Long historialId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "habito_id", nullable = false)
    private HabitosEstiloDeVida habito;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_habito_id", nullable = false)
    private TipoHabito tipoHabito;

    @Column(name = "descripcion", nullable = false, length = 255)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel", nullable = false, length = 10)
    private NivelHabito nivel;

    @Column(name = "frecuencia", nullable = false, length = 100)
    private String frecuencia;

    @Column(name = "observaciones", length = 500)
    private String observaciones;

    // Cuando y quien escribio ESTA version (no quien la reemplazo).
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actualizado_por")
    private Persona actualizadoPor;

    @Column(name = "reemplazado_en", nullable = false)
    private LocalDateTime reemplazadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reemplazado_por")
    private Persona reemplazadoPor;
}
