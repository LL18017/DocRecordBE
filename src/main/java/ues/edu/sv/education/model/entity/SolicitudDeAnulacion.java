package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import lombok.*;
import ues.edu.sv.education.model.enums.EstadoSolicitudDeAnulacion;
import ues.edu.sv.education.model.enums.TipoDocumentoAnulable;

import java.time.LocalDateTime;

/**
 * El pedido de anular una consulta o una receta fuera de la ventana de
 * anulacion directa (V35). Mientras esta PENDIENTE el documento sigue
 * vigente; se anula cuando junta sus aprobaciones.
 */
@Entity
@Table(name = "solicitudes_de_anulacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitudDeAnulacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "solicitud_id")
    private Long solicitudId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoDocumentoAnulable tipo;

    // Siempre: en una de RECETA es la consulta de la que salio.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consulta_id", nullable = false)
    private Consulta consulta;

    // Solo en las de RECETA.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescripcion_id")
    private Prescripcion prescripcion;

    @Column(name = "motivo", nullable = false, length = 500)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitada_por", nullable = false)
    private User solicitadaPor;

    @Column(name = "solicitada_en", nullable = false)
    private LocalDateTime solicitadaEn;

    // Copia del parametro al pedirla: cambiar la configuracion despues no
    // mueve la meta de esta solicitud.
    @Column(name = "aprobaciones_requeridas", nullable = false)
    private Integer aprobacionesRequeridas;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private EstadoSolicitudDeAnulacion estado = EstadoSolicitudDeAnulacion.PENDIENTE;

    @Column(name = "resuelta_en")
    private LocalDateTime resueltaEn;

    // Quien dio la ultima aprobacion o el rechazo. Null si se cancelo sola.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resuelta_por")
    private User resueltaPor;

    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;

    // Las aprobaciones no se mapean aqui: se guardan y se cuentan por su
    // repositorio, que es donde la clave (solicitud, usuario) las protege.
}
