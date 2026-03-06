package ues.edu.sv.education.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.UniqueElements;

@Entity
@Table(name = "users")
@Getter @Setter
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
}
