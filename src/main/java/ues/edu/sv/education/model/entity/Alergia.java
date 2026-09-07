package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

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
    private Integer alergiaID;

    @NotBlank(message = "El nombre de la alergia no puede estar vacío")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    @Column(name = "nombre", nullable = false)
    private String nombre;

    @NotBlank(message = "El tipo de alergia no puede estar vacío")
    @Size(max = 100, message = "El tipo no puede superar los 100 caracteres")
    @Column(name = "tipo", nullable = false)
    private String tipo;

    @NotBlank(message = "La severidad no puede estar vacía")
    @Size(max = 50, message = "La severidad no puede superar los 50 caracteres")
    @Column(name = "severidad", nullable = false)
    private String severidad;

    @NotBlank(message = "La reacción reportada no puede estar vacía")
    @Size(max = 255, message = "La reacción reportada no puede superar los 255 caracteres")
    @Column(name = "reaccion_reportada", nullable = false)
    private String reaccionReportada;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            referencedColumnName = "user_id",
            nullable = false
    )
    private User user;
}