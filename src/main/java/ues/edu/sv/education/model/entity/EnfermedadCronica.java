package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Table(name = "enfermedades_cronicas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnfermedadCronica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "enfermedad_cronica_id")
    private Integer enfermedadCronicaID;

    @NotBlank(message = "El nombre de la enfermedad no puede estar vacío")
    @Size(max = 100)
    @Column(name = "nombre", nullable = false)
    private String nombre;

    @NotNull(message = "El año es obligatorio")
    @Column(name = "anio", nullable = false)
    private Integer anio;

    @NotBlank(message = "El tratamiento no puede estar vacío")
    @Size(max = 255)
    @Column(name = "tratamiento", nullable = false)
    private String tratamiento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            referencedColumnName = "user_id",
            nullable = false
    )
    private User user;
}