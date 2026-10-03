package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

// Ver el comentario en Medico sobre por que persona_id es PK y FK a la vez
// en lugar de usar @Inheritance.
@Entity
@Table(name = "enfermeras")
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class Enfermera {

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

    @Column(name = "activo", nullable = false)
    private boolean activo;
}
