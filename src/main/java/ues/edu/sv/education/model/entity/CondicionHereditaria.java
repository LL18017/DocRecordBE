package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import ues.edu.sv.education.model.enums.Parentesco;

/**
 * Una condicion hereditaria del expediente (HU-13): una enfermedad de la
 * familia del paciente y el parentesco del familiar afectado.
 *
 * Cuelga de Paciente, no de User (ver V19): es parte del expediente, y la
 * mayoria de los pacientes no tiene cuenta en el sistema.
 */
@Entity
@Table(name = "condiciones_hereditarias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CondicionHereditaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "condicion_hereditaria_id")
    private Integer condicionHereditariaId;

    @NotBlank(message = "El nombre no puede estar vacío")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    @Column(name = "nombre", nullable = false)
    private String nombre;

    @NotNull(message = "El parentesco es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(name = "parentesco", nullable = false, length = 10)
    private Parentesco parentesco;

    @Size(max = 255, message = "Las observaciones no pueden superar los 255 caracteres")
    @Column(name = "observaciones")
    private String observaciones;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;
}
