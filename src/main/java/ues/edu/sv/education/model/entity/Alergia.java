package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import ues.edu.sv.education.model.enums.SeveridadDeAlergia;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Una alergia del expediente (HU-11): la sustancia, la reaccion que provoca,
 * su severidad y desde cuando se sabe.
 *
 * Cuelga de Paciente, no de User (ver V20), igual que las condiciones
 * hereditarias: es parte del expediente, y la mayoria de los pacientes no
 * tiene cuenta en el sistema.
 *
 * Eliminar es baja logica: se llenan `eliminadaPor` y `eliminadaEn` y la fila
 * se queda. Una alergia que se quito por error tiene que poder encontrarse.
 */
@Entity
@Table(name = "alergias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alergia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alergia_id")
    private Integer alergiaId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @NotBlank
    @Size(max = 100)
    @Column(name = "sustancia", nullable = false, length = 100)
    private String sustancia;

    @NotBlank
    @Size(max = 255)
    @Column(name = "reaccion", nullable = false)
    private String reaccion;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "severidad", nullable = false, length = 10)
    private SeveridadDeAlergia severidad;

    // El dia en que se detecto la alergia, no el del registro.
    @NotNull
    @Column(name = "fecha_deteccion", nullable = false)
    private LocalDate fechaDeteccion;

    // Null solo en las filas heredadas de V18, que nunca guardaron autor.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrada_por")
    private Persona registradaPor;

    @NotNull
    @Column(name = "registrada_en", nullable = false)
    private LocalDateTime registradaEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eliminada_por")
    private Persona eliminadaPor;

    @Column(name = "eliminada_en")
    private LocalDateTime eliminadaEn;

    /** Sigue vigente: nadie la ha dado de baja. */
    public boolean estaVigente() {
        return eliminadaEn == null;
    }
}
