package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import ues.edu.sv.education.model.enums.EstadoConsulta;

import java.time.LocalDateTime;

/**
 * El acto medico: un medico atendio a un paciente en una fecha.
 *
 * Apunta a Paciente y a Medico, no a Persona: una consulta solo tiene sentido
 * entre alguien registrado como paciente y alguien registrado como medico. La
 * migracion V6 sostiene lo mismo con las claves foraneas.
 *
 * No mapea sus prescripciones. La receta conoce a su consulta y no al reves,
 * porque el borrado en cascada de las recetas lo hace PostgreSQL (ON DELETE
 * CASCADE en V6) y no Hibernate: la restriccion protege tambien a las
 * correcciones hechas por SQL directo, que es donde una cascada escrita en
 * Java no llega.
 */
@Entity
@Table(name = "consultas")
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class Consulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consulta_id")
    private Long consultaId;

    @NotNull(message = "El paciente es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    // Sale del token, nunca del cuerpo de la peticion (ver ConsultaService).
    @NotNull(message = "El medico es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    // Puede ser null: un medico que aun no registro ninguna sucursal atiende
    // igual. Ver el comentario de la columna en V6.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinica_id")
    private Clinicas clinica;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    // TEXT en la base. Un motivo de consulta es prosa del paciente ("me duele
    // desde hace tres dias, sobre todo al respirar"), no una etiqueta corta.
    @Column(name = "motivo")
    private String motivo;

    // TEXT en la base, y el campo mas protegido del sistema: solo lo escribe
    // un medico. La regla se impone en ConsultaService, que es quien sabe
    // quien esta autenticado.
    @Column(name = "diagnostico")
    private String diagnostico;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoConsulta estado;
}
