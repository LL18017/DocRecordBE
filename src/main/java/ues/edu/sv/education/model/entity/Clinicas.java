package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Table(name = "clinicas")
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class Clinicas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "clinica_id")
    private Integer clinicaId;

    @NotBlank(message = "El nombre no puede estar vacío")
    @NotNull(message = "El nombre es obligatorio")
    @Size(max = 100)
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "latitud")
    private Double latitud;

    @Column(name = "longitud")
    private Double longitud;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            referencedColumnName = "user_id",
            nullable = false
    )
    private User user;
}