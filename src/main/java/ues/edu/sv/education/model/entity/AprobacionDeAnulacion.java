package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * El visto bueno de un administrador a una solicitud de anulacion (V35). La
 * clave es (solicitud, usuario): cada administrador cuenta una sola vez.
 */
@Entity
@Table(name = "aprobaciones_de_anulacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(AprobacionDeAnulacion.Clave.class)
public class AprobacionDeAnulacion {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitud_id")
    private SolicitudDeAnulacion solicitud;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User usuario;

    @Column(name = "aprobada_en", nullable = false)
    private LocalDateTime aprobadaEn;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Clave implements Serializable {
        private Long solicitud;
        private Integer usuario;
    }
}
