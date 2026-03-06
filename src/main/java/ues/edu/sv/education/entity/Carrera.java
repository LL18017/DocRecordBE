package ues.edu.sv.education.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import ues.edu.sv.education.dto.Carrera.CarreraDto;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "carrera")
@Getter
@Setter
public class Carrera {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "carrera_id")
    Integer carreraId;

    @Column(name = "name",unique = true,nullable = false)
    String name;
    @ManyToMany(mappedBy = "carreras",fetch = FetchType.EAGER)
    List<Facultad> facultades =new ArrayList<>();

    public Carrera() {
    }

    public Carrera(Integer carreraId, String nombre) {
        this.carreraId = carreraId;
        this.name = nombre;
    }

    public CarreraDto toDto() {
        return new CarreraDto(this.carreraId, this.name);
    }

}
