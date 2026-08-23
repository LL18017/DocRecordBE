package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

// Ver el comentario en Medico sobre por que persona_id es PK y FK a la vez
// en lugar de usar @Inheritance.
@Entity
@Table(name = "pacientes")
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class Paciente {

    @Id
    @Column(name = "persona_id")
    private Long personaId;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "persona_id")
    private Persona persona;

    @NotBlank(message = "El expediente no puede estar vacio")
    @NotNull(message = "El expediente es obligatorio")
    @Size(max = 12)
    @Column(name = "expediente", nullable = false, unique = true, length = 12)
    private String expediente;

    @Size(max = 3)
    @Column(name = "tipo_sangre", length = 3)
    private String tipoSangre;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;
}
