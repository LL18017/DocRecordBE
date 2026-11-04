package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Una vez que alguien abrio o descargo un expediente desde el portal del
 * paciente (HU-34 criterio 6). Ver V36 para por que es una tabla aparte de
 * bitacora_de_usuarios y por que usuario_id no tiene FK.
 */
@Entity
@Table(name = "accesos_al_expediente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccesoAlExpediente {

    /** Se abrio el expediente en el portal. */
    public static final String VISTA = "VISTA";
    /** Se descargo el expediente en PDF. */
    public static final String PDF = "PDF";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "acceso_id")
    private Long accesoId;

    /** persona_id del paciente cuyo expediente se leyo. */
    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    /** user_id de quien lo leyo. Sin FK: el rastro sobrevive a la cuenta. */
    @Column(name = "usuario_id", nullable = false)
    private Integer usuarioId;

    /** Correo con que inicio sesion quien lo leyo, tal como era en ese momento. */
    @Column(name = "correo_de_sesion", nullable = false)
    private String correoDeSesion;

    @Column(name = "tipo", nullable = false, length = 10)
    private String tipo;

    @Column(name = "accedido_en", nullable = false)
    private LocalDateTime accedidoEn;
}
