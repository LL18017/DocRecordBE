package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Un renglon de la receta: que medicamento, cuanto, por que via, cada cuanto y
 * por cuanto tiempo.
 *
 * Es una tabla y no un texto libre dentro de la receta para que se pueda
 * responder "que pacientes toman X", que es de lo poco que un expediente en
 * papel no puede hacer. Desde HU-23 esa pregunta se responde por
 * `catalogo`, no comparando textos.
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

    /**
     * El producto del catalogo que se receto (HU-23).
     *
     * NULO en las recetas emitidas antes del catalogo, que eran texto libre y
     * no tienen a que fila apuntar (ver V22). Las nuevas lo llevan siempre:
     * lo exige PrescripcionService al emitir.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medicamento_id")
    private Medicamento catalogo;

    /**
     * El nombre tal como quedo escrito en la receta.
     *
     * Para las recetas nuevas es la foto de catalogo.descripcion() al
     * emitirla, y no se recalcula: si el catalogo se corrige despues, la
     * receta que ya se entrego tiene que seguir diciendo lo mismo que el
     * papel. Para las antiguas es el texto libre que se escribio entonces.
     */
    @NotBlank(message = "El medicamento no puede estar vacio")
    @Size(max = 300)
    @Column(name = "medicamento", nullable = false, length = 300)
    private String medicamento;

    // HU-22, criterio 2: dosis, via de administracion, frecuencia y duracion
    // son obligatorias. Una receta sin ellas no dice como tomar el
    // medicamento, que es para lo que existe. (Las anteriores a V31 que no las
    // tenian quedaron con "No especificada".)
    @Size(max = 80)
    @Column(name = "dosis", nullable = false, length = 80)
    private String dosis;

    @Size(max = 60)
    @Column(name = "via_administracion", nullable = false, length = 60)
    private String viaAdministracion;

    @Size(max = 80)
    @Column(name = "frecuencia", nullable = false, length = 80)
    private String frecuencia;

    @Size(max = 80)
    @Column(name = "duracion", nullable = false, length = 80)
    private String duracion;
}
