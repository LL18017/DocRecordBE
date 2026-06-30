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
    @NotBlank(message = "El nombre no puede estar vacio")
    @NotNull(message = "El nombre es un propiedda obligatoria")
    @Column(name = "name",nullable = false)
    @Size(max = 100)
    private  String name;
    @Email
    @Column(name = "email", unique = true, nullable = false)
    private  String email;
    @Size(max = 255)
    @Column(name = "password")
    private String password;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles;
}
