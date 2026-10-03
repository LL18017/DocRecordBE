package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

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

    // Quien firma la receta. Sale del token, igual que en Consulta.
    @NotNull(message = "El medico es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

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
