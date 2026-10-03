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
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
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

    // ── Donde esta la clinica de verdad (HU-26, ver V16) ────────────────────
    // Un punto en un mapa no le sirve a un paciente que busca donde atenderse:
    // necesita saber en que municipio queda, como llegar, a que telefono
    // llamar y a que hora abren.
    @Size(max = 40)
    @Column(name = "departamento", length = 40)
    private String departamento;

    @Size(max = 60)
    @Column(name = "municipio", length = 60)
    private String municipio;

    @Size(max = 200)
    @Column(name = "direccion", length = 200)
    private String direccion;

    @Size(max = 20)
    @Column(name = "telefono", length = 20)
    private String telefono;

    /** Texto libre: "Lunes a viernes, 7:00 a 16:00". */
    @Size(max = 120)
    @Column(name = "horario", length = 120)
    private String horario;

    /**
     * ACTIVA o INACTIVA.
     *
     * Dar de baja una clinica la retira del directorio y de la seleccion de
     * sede, pero no borra nada: las consultas que se atendieron en ella
     * ocurrieron ahi, y el personal asignado sigue asignado. Misma decision que
     * con el estado del paciente.
     */
    @NotNull
    @Size(max = 10)
    @Column(name = "estado", nullable = false, length = 10)
    @Builder.Default
    private String estado = ESTADO_ACTIVA;

    public static final String ESTADO_ACTIVA = "ACTIVA";
    public static final String ESTADO_INACTIVA = "INACTIVA";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            referencedColumnName = "user_id",
            nullable = false
    )
    private User user;
}