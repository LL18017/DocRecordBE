package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import lombok.*;
import ues.edu.sv.education.model.enums.NivelHabito;

import java.time.LocalDateTime;

/**
 * Un habito del expediente (HU-14): su tipo, que tan riesgoso es, con que
 * frecuencia ocurre y las observaciones del personal. La descripcion dice QUE
 * es; la frecuencia y las observaciones van aparte para que no queden
 * mezcladas en un texto libre.
 *
 * `actualizadoEn` / `actualizadoPor` son de la ultima escritura (alta o
 * edicion). Lo que se reemplaza en una edicion no se pierde: queda en
 * HabitoHistorial.
 */
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

    @Column(name = "frecuencia", nullable = false, length = 100)
    private String frecuencia;

    @Column(name = "observaciones", length = 500)
    private String observaciones;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    // Null solo en los habitos anteriores a V29, que no guardaron autor.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actualizado_por")
    private Persona actualizadoPor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_habito_id", nullable = false)
    private TipoHabito tipoHabito;

    // ── Anulacion (HU-14, DEF-03, migracion V42) ───────────────────────────
    // Un habito no se borra: se anula dejando motivo, quien y cuando. El
    // historial de versiones se conserva. Builder.Default para que .builder()
    // no deje el estado en null.
    public static final String ACTIVO = "ACTIVO";
    public static final String ANULADO = "ANULADO";

    @Builder.Default
    @Column(name = "estado", nullable = false, length = 10)
    private String estado = ACTIVO;

    @Column(name = "motivo_anulacion", length = 500)
    private String motivoAnulacion;

    @Column(name = "anulado_en")
    private LocalDateTime anuladoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anulado_por")
    private Persona anuladoPor;

    public boolean estaAnulado() {
        return ANULADO.equals(estado);
    }
}
