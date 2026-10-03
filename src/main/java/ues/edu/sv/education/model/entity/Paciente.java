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

    /**
     * ACTIVO o INACTIVO (ver V12).
     *
     * Dar de baja a un paciente lo saca de los listados de trabajo diario,
     * pero NO borra nada: su expediente, sus consultas, sus constantes y sus
     * recetas siguen existiendo y siguen siendo consultables. Un expediente
     * clinico no se borra, y por eso esto es un estado y no un DELETE.
     *
     * Es String y no un enum de Java a proposito: el unico sitio donde el
     * conjunto de valores debe estar cerrado es la base -- lo cierra el CHECK
     * de V12 --, y un enum aqui obligaria a tocar codigo para anadir un tercer
     * estado sin ganar nada a cambio.
     */
    @NotNull
    @Size(max = 10)
    @Column(name = "estado", nullable = false, length = 10)
    @Builder.Default
    private String estado = ESTADO_ACTIVO;

    public static final String ESTADO_ACTIVO = "ACTIVO";
    public static final String ESTADO_INACTIVO = "INACTIVO";
}
