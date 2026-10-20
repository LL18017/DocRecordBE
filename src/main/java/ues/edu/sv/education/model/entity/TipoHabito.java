package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tipos_habito")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TipoHabito {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "id")
    private Integer id;

    @Column(name = "descripcion", nullable = false, length = 255)
    private String descripcion;
}