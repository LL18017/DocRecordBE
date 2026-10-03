package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Entity
@Table(name = "role")
@RequiredArgsConstructor
@Getter
@Setter
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    Integer roleId;
    /**
     * Uno de los cuatro nombres de RolesEnum, ni mas ni menos.
     *
     * Quien lo garantiza de verdad es la base -- NOT NULL + UNIQUE + CHECK,
     * puestos en la migracion V7 -- porque una anotacion de JPA solo protege
     * lo que pasa por Hibernate, y por esta tabla pasan tambien data.sql, las
     * migraciones y cualquiera con un psql abierto. Las anotaciones estan aqui
     * para que el invariante se lea al mirar la entidad, no como defensa.
     */
    @Column(name = "name", nullable = false, unique = true)
    String name;

    @ManyToMany(mappedBy = "roles")
    private Set<User> users;


    public Role(Integer idRole, String name) {
        this.roleId=idRole;
        this.name=name;
    }
}
