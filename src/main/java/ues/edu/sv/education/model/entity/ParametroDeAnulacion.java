package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import lombok.*;
import ues.edu.sv.education.model.enums.TipoDocumentoAnulable;

import java.time.LocalDateTime;

/**
 * Las reglas de anulacion de un tipo de documento, que el administrador
 * cambia sin redesplegar (V35).
 *
 * La regla completa vive en AnulacionService; aqui solo los numeros.
 */
@Entity
@Table(name = "parametros_de_anulacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParametroDeAnulacion {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20)
    private TipoDocumentoAnulable tipo;

    // Cuantos minutos despues de registrado se anula sin pedir aprobacion.
    // 0 = toda anulacion pasa por aprobacion.
    @Column(name = "minutos_anulacion_directa", nullable = false)
    private Integer minutosAnulacionDirecta;

    // Cuantos administradores distintos de quien la pide tienen que aprobar.
    @Column(name = "aprobaciones_requeridas", nullable = false)
    private Integer aprobacionesRequeridas;

    // Pasados estos dias ya no se anula. Null = sin tope.
    @Column(name = "dias_maximos")
    private Integer diasMaximos;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actualizado_por")
    private User actualizadoPor;
}
