package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Un renglon de la receta: que medicamento, cuanto, cada cuanto y por cuanto
 * tiempo.
 *
 * Es una tabla y no un texto libre dentro de la receta para que se pueda
 * responder "que pacientes toman X", que es de lo poco que un expediente en
 * papel no puede hacer.
 */
@Entity
@Table(name = "prescripcion_medicamentos")
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class PrescripcionMedicamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescripcion_id", nullable = false)
    private Prescripcion prescripcion;

    @NotBlank(message = "El medicamento no puede estar vacio")
    @Size(max = 160)
    @Column(name = "medicamento", nullable = false, length = 160)
    private String medicamento;

    // Dosis, frecuencia y duracion pueden faltar: hay indicaciones que
    // legitimamente no las llevan ("suspender el tratamiento anterior").
    @Size(max = 80)
    @Column(name = "dosis", length = 80)
    private String dosis;

    @Size(max = 80)
    @Column(name = "frecuencia", length = 80)
    private String frecuencia;

    @Size(max = 80)
    @Column(name = "duracion", length = 80)
    private String duracion;
}
