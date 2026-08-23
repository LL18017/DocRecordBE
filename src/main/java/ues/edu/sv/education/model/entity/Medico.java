package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

// Clave primaria compartida con Persona (persona_id es PK y FK a la vez),
// no @Inheritance: una misma persona puede tener fila aqui y en Enfermera o
// Paciente al mismo tiempo, y con herencia de tabla unica una fila solo
// pertenece a un subtipo.
@Entity
@Table(name = "medicos")
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class Medico {

    @Id
    @Column(name = "persona_id")
    private Long personaId;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "persona_id")
    private Persona persona;

    @Size(max = 20)
    @Column(name = "registro_junta", unique = true, length = 20)
    private String registroJunta;

    @NotNull(message = "La especialidad es obligatoria")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especialidad_id", nullable = false)
    private Especialidad especialidad;

    @Column(name = "activo", nullable = false)
    private boolean activo;
}
