package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tipos_cita")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TipoCita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tipo_cita_id")
    private Integer tipoCitaId;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "descripcion", length = 255)
    private String descripcion;
}