package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * El permiso temporal para ponerle una contrasena nueva a una cuenta (HU-04).
 *
 * ── Por que no es un VerificationToken con un campo `tipo` ────────────────
 * VerificationToken ya existe, tiene `expiresAt` y `used`, y la forma es
 * identica: la tentacion de anadirle un discriminador y ahorrarse esta clase
 * es real. Se descarto a proposito.
 *
 * Un token de confirmacion de correo y uno de restablecimiento NO valen lo
 * mismo. El primero habilita una cuenta; el segundo la entrega. Si comparten
 * tabla, lo unico que impide que uno sirva para lo del otro es que TODAS las
 * consultas se acuerden de filtrar por el tipo -- y hoy hay tres que no
 * filtran nada, porque se escribieron cuando el tipo no existia:
 * findByToken (AuthService.confirmToken) y los dos deleteAllByUser
 * (UserService.asignarContrasena y AuthService.registrarMedico). La primera
 * convertiria cualquier correo de "confirma tu cuenta" en una forma de
 * cambiarle la contrasena a esa cuenta; las otras dos borrarian sin querer los
 * enlaces de recuperacion pendientes.
 *
 * Con clase y tabla propias eso no puede pasar por olvido: confirmToken busca
 * en `verification_token` y ahi no hay tokens de restablecimiento. Lo sostiene
 * el esquema, no la disciplina de quien escriba la proxima consulta.
 * Ver tambien la cabecera de V14.
 *
 * ── El valor se guarda en claro, igual que en VerificationToken ───────────
 * Es lo que hace hoy el token de confirmacion y se mantiene por coherencia,
 * pero tiene una consecuencia que conviene tener escrita: quien consiga leer
 * esta tabla puede usar cualquier enlace vigente. Guardar solo un resumen
 * (SHA-256 del token, comparando el resumen al canjearlo) lo evitaria sin
 * cambiar nada de cara al usuario. Queda pendiente para decidirlo junto con el
 * mismo cambio en VerificationToken, no en una tabla suelta.
 */
@Entity
@Table(name = "token_restablecimiento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TokenDeRestablecimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long tokenId;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User usuario;

    /**
     * Cuando deja de servir. Se guarda el instante y no la duracion: si manana
     * el plazo cambia, los enlaces YA enviados tienen que seguir venciendo
     * cuando se prometio en su correo, no alargarse retroactivamente.
     */
    @Column(name = "vence_en", nullable = false)
    private LocalDateTime venceEn;

    @Column(nullable = false)
    private boolean usado = false;

    public boolean estaVencido() {
        return LocalDateTime.now().isAfter(venceEn);
    }

    /**
     * Un solo predicado para "este enlace sirve".
     *
     * Existe para que quien lo canjea no tenga que acordarse de comprobar las
     * dos cosas: olvidar `usado` deja el enlace reutilizable y olvidar
     * `venceEn` lo vuelve eterno. Las dos son la misma decision.
     */
    public boolean sirve() {
        return !usado && !estaVencido();
    }
}
