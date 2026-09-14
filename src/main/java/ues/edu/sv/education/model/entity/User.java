package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.Set;

@Entity
@Table(name = "users")
@Getter @Setter
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "user_id")
    private  Integer UserID;
    @NotNull(message = "La persona es obligatoria")
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "persona_id", nullable = false, unique = true)
    private Persona persona;
    @Email
    @Column(name = "email", unique = true, nullable = false)
    private  String email;
    @Size(max = 255)
    @Column(name = "password")
    private String password;
    /** El correo de esta cuenta esta confirmado (ver V15). */
    @Column(nullable = false)
    private boolean enabled = false;

    /**
     * La organizacion le permite entrar (HU-05 criterio 3, ver V15).
     *
     * Deliberadamente separada de `enabled`. Son dos preguntas distintas
     * -- "confirmo su correo" y "puede entrar hoy" -- y meterlas en la misma
     * columna hacia que asignarle una contrasena a alguien desactivado lo
     * reactivara sin que nadie lo pidiera.
     *
     * Solo la mueve un administrador; ninguna accion del propio usuario la
     * toca. Desactivar no borra nada: las consultas que firmo un medico siguen
     * firmadas por el aunque su cuenta quede cerrada, porque ocurrieron.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles;

    // Las clinicas que este usuario REGISTRO. `clinicas.user_id` es la
    // propiedad de la sede, no el lugar donde alguien trabaja.
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<Clinicas> clinicas;

    // Las clinicas donde este usuario TRABAJA sin haberlas registrado (ver
    // V10). Son dos cosas distintas y hacen falta las dos: una enfermera nunca
    // da de alta una sede -trabaja en la que registro un medico-, asi que con
    // solo la propiedad su lista salia vacia y la pantalla de seleccion de
    // clinica la dejaba encallada.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "clinica_personal",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "clinica_id")
    )
    private Set<Clinicas> clinicasAsignadas;

    /**
     * El correo se guarda SIEMPRE en minusculas y sin espacios alrededor.
     *
     * ── Por que aqui y no en cada servicio ────────────────────────────────
     * Hay cuatro sitios que construyen un User con su correo -el registro
     * publico, POST /user, el alta de enfermeria y AdminBootstrap- y no hay
     * nada que impida que aparezca un quinto. Repartir un .toLowerCase() por
     * cada uno funciona hasta que alguien anade el siguiente y se olvida, y el
     * fallo no se ve: la cuenta se crea bien y solo falla al intentar entrar.
     * Un callback de JPA se ejecuta antes de CUALQUIER insert o update de esta
     * entidad, venga de donde venga.
     *
     * ── Por que hace falta normalizar ─────────────────────────────────────
     * La busqueda ignora mayusculas (findByEmailIgnoreCase, exigido por HU-02
     * criterio 4). Si la base guardara `Ana@ues.edu.sv` y `ana@ues.edu.sv` como
     * dos cuentas distintas -cosa que el UNIQUE de la columna permite, porque
     * compara byte a byte-, esa busqueda encontraria dos filas donde el codigo
     * espera una y el login de ambas respondaria 500. V11 pone ademas un indice
     * unico sobre LOWER(email) para que la base lo impida aunque este callback
     * desapareciera.
     */
    @PrePersist
    @PreUpdate
    private void normalizarCorreo() {
        if (email != null) email = email.trim().toLowerCase();
    }
}
