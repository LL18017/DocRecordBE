package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Un producto del catalogo de medicamentos (HU-23).
 *
 * Es lo que un medico puede recetar: la receta ya no lleva texto libre sino
 * una referencia a esta fila (ver PrescripcionMedicamento.catalogo).
 *
 * El principio activo es un campo propio y no parte del nombre porque HU-24
 * lo cruza contra las alergias del paciente; dentro del nombre habria que
 * adivinarlo.
 *
 * No se borra nunca: se desactiva. Ver el comentario de `activo` en V22.
 */
@Entity
@Table(name = "medicamentos")
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class Medicamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "medicamento_id")
    private Long medicamentoId;

    @NotBlank
    @Size(max = 80)
    @Column(name = "nombre_generico", nullable = false, length = 80)
    private String nombreGenerico;

    @NotBlank
    @Size(max = 80)
    @Column(name = "nombre_comercial", nullable = false, length = 80)
    private String nombreComercial;

    @NotBlank
    @Size(max = 150)
    @Column(name = "principio_activo", nullable = false, length = 150)
    private String principioActivo;

    @NotBlank
    @Size(max = 50)
    @Column(name = "presentacion", nullable = false, length = 50)
    private String presentacion;

    @NotBlank
    @Size(max = 40)
    @Column(name = "concentracion", nullable = false, length = 40)
    private String concentracion;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    /**
     * Como se lee el producto en una sola linea: "Amoxicilina 500 mg (Amoxil),
     * Capsula".
     *
     * Es tambien la foto que se guarda en el renglon de la receta al emitirla
     * (prescripcion_medicamentos.medicamento), asi que vive en la entidad y no
     * en un DTO: si el formato cambiara en un solo sitio, el catalogo y las
     * recetas dirian cosas distintas del mismo producto.
     */
    public String descripcion() {
        return nombreGenerico + " " + concentracion + " (" + nombreComercial + "), " + presentacion;
    }
}
