package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import ues.edu.sv.education.model.enums.EstadoDeAntecedente;
import ues.edu.sv.education.model.enums.TipoDeAntecedente;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un antecedente patologico del expediente (HU-12): una enfermedad previa,
 * una cirugia o una hospitalizacion.
 *
 * Apunta a Paciente y a Medico, igual que Consulta: el antecedente es parte
 * del expediente de alguien registrado como paciente, y lo registra alguien
 * registrado como medico. Ver V19.
 */
@Entity
@Table(name = "antecedentes_patologicos")
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class AntecedentePatologico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "antecedente_id")
    private Long antecedenteId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoDeAntecedente tipo;

    @NotBlank
    @Size(max = 500)
    @Column(name = "descripcion", nullable = false, length = 500)
    private String descripcion;

    @NotNull
    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoDeAntecedente estado;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    @NotNull
    @Column(name = "registrado_en", nullable = false)
    private LocalDateTime registradoEn;
}
