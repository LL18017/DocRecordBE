package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Una huella de lo que un administrador hizo sobre una cuenta (HU-05
 * criterio 5). Ver V27 para por que no tiene FK hacia users.
 */
@Entity
@Table(name = "bitacora_de_usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BitacoraDeUsuario {

    public static final String ALTA = "ALTA";
    public static final String ACTIVACION = "ACTIVACION";
    public static final String DESACTIVACION = "DESACTIVACION";
    public static final String ROL_ASIGNADO = "ROL_ASIGNADO";
    public static final String ROL_RETIRADO = "ROL_RETIRADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_afectado_id", nullable = false)
    private Integer usuarioAfectadoId;

    @Column(name = "accion", nullable = false, length = 30)
    private String accion;

    @Column(name = "detalle", length = 255)
    private String detalle;

    /** Correo de quien operó, tal como era en ese momento. */
    @Column(name = "realizado_por", nullable = false, length = 100)
    private String realizadoPor;

    @Column(name = "realizado_en", nullable = false)
    private LocalDateTime realizadoEn;
}
