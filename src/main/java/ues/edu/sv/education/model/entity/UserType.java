package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Entity
@Table(name = "user_type")
@Getter
@Setter
@NoArgsConstructor
public class UserType {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "user_type_id")
    private  Integer userTypeID;
    @NotBlank(message = "El nombre no puede estar vacio")
    @NotNull(message = "El nombre es un propiedda obligatoria")
    @Column(name = "name",nullable = false)
    @Size(max = 100)
    private  String name;
    @OneToMany(mappedBy = "userType")
    private Set<User> users;
}
