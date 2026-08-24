package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

@Entity
@Table(name = "persona")
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class Persona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "persona_id")
    private Long personaId;

    @Size(max = 10)
    @Column(name = "dui", unique = true, length = 10)
    private String dui;

    @NotBlank(message = "Los nombres no pueden estar vacios")
    @NotNull(message = "Los nombres son obligatorios")
    @Size(max = 80)
    @Column(name = "nombres", nullable = false, length = 80)
    private String nombres;

    @NotBlank(message = "Los apellidos no pueden estar vacios")
    @NotNull(message = "Los apellidos son obligatorios")
    @Size(max = 80)
    @Column(name = "apellidos", nullable = false, length = 80)
    private String apellidos;

    // Nula en el alta publica de un medico (solo se piden nombre y correo).
    // El servicio de alta de pacientes es quien exige que este dato exista.
    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    // La columna es CHAR(1) (ancho fijo), no VARCHAR: sin @JdbcTypeCode
    // Hibernate mapea String a varchar por defecto y ddl-auto=validate
    // rechaza el arranque por el tipo.
    @Pattern(regexp = "[MF]", message = "El sexo debe ser M o F")
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sexo", length = 1)
    private String sexo;

    @Size(max = 20)
    @Column(name = "telefono", length = 20)
    private String telefono;

    @Size(max = 255)
    @Column(name = "direccion")
    private String direccion;

    @Email(message = "El correo no tiene un formato valido")
    @Size(max = 255)
    @Column(name = "email")
    private String email;
}
