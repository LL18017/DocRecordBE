package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Table(name = "especialidades")
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class Especialidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "especialidad_id")
    private Long especialidadId;

    @NotBlank(message = "El nombre no puede estar vacio")
    @NotNull(message = "El nombre es obligatorio")
    @Size(max = 80)
    @Column(name = "nombre", nullable = false, unique = true, length = 80)
    private String nombre;

    @Column(name = "activa", nullable = false)
    private boolean activa;
}
