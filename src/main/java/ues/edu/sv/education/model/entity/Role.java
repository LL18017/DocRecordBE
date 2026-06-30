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
    @Column(name = "name")
    String name;

    @ManyToMany(mappedBy = "roles")
    private Set<User> users;


    public Role(Integer idRole, String name) {
        this.roleId=idRole;
        this.name=name;
    }
}
