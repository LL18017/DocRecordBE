package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * El enlace para que una persona cree su propia contrasena y active la cuenta
 * que el administrador le abrio (V35).
 *
 * Tabla propia, no un tipo dentro de token_restablecimiento ni de
 * verification_token: ver la cabecera de TokenDeRestablecimiento. Cada enlace
 * concede algo distinto y no debe poder servir para lo de otro.
 */
@Entity
@Table(name = "invitaciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invitacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invitacion_id")
    private Long invitacionId;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User usuario;

    @Column(name = "vence_en", nullable = false)
    private LocalDateTime venceEn;

    @Column(name = "usada", nullable = false)
    private boolean usada;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    /** Ni usada ni vencida. Un solo predicado, como TokenDeRestablecimiento.sirve. */
    public boolean sirve() {
        return !usada && LocalDateTime.now().isBefore(venceEn);
    }
}
