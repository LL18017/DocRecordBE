package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import ues.edu.sv.education.model.enums.EstadoPrescripcion;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * La receta que sale de una consulta.
 *
 * Guarda su propio medico aunque la consulta ya tenga uno: es la FIRMA del
 * documento. Ver el comentario de la columna en la migracion V6.
 *
 * Los medicamentos si cuelgan de aqui en Java (cascade + orphanRemoval)
 * ademas de en la base: una receta se guarda de una sola vez, con sus
 * renglones, y nunca hay un renglon que valga sin su receta.
 */
@Entity
@Table(name = "prescripciones")
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class Prescripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prescripcion_id")
    private Long prescripcionId;

    @NotNull(message = "La consulta es obligatoria")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consulta_id", nullable = false)
    private Consulta consulta;

    // Quien firma la receta: el medico de la consulta (HU-22, criterio 1),
    // que ademas tiene que ser quien la emite; ver PrescripcionService.crear.
    @NotNull(message = "El medico es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    /**
     * Por que se receto pese a una alergia registrada del paciente (HU-24).
     * Vacia si la receta no tuvo coincidencias: es parte del documento, no de
     * la bitacora, para que quien lea la receta vea la razon junto a ella.
     */
    @Column(name = "justificacion_alergia", length = 500)
    private String justificacionAlergia;

    /**
     * EMITIDA o ANULADA (HU-22, criterio 3). Una receta no se borra: se anula
     * y sigue visible, marcada. Ver V31.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    @Builder.Default
    private EstadoPrescripcion estado = EstadoPrescripcion.EMITIDA;

    // El rastro de la anulacion: por que, quien y cuando. Los tres van juntos
    // o no va ninguno (lo garantiza un CHECK en V31).
    @Column(name = "motivo_anulacion", length = 500)
    private String motivoAnulacion;

    @Column(name = "anulada_en")
    private LocalDateTime anuladaEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anulada_por")
    private User anuladaPor;

    @Builder.Default
    @OneToMany(mappedBy = "prescripcion",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<PrescripcionMedicamento> medicamentos = new ArrayList<>();

    /**
     * Agrega un renglon manteniendo los dos lados de la relacion.
     *
     * Sin fijar el lado dueno (medicamento.prescripcion) Hibernate insertaria
     * la fila con prescripcion_id nulo y moriria contra el NOT NULL de la
     * columna.
     */
    public void agregarMedicamento(PrescripcionMedicamento medicamento) {
        if (medicamentos == null) medicamentos = new ArrayList<>();
        medicamento.setPrescripcion(this);
        medicamentos.add(medicamento);
    }
}
