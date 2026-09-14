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
    @Column(nullable = false)
    private boolean enabled = false;
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
}
