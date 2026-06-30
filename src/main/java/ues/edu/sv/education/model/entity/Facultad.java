package ues.edu.sv.education.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ues.edu.sv.education.model.dto.Carrera.CarreraDto;
import ues.edu.sv.education.model.dto.Facultad.FacultadDto;
import ues.edu.sv.education.model.dto.Facultad.FacultadRequestDto;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "facultad")
@Getter
@Setter
public class Facultad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "facultad_id")
    private Integer facultadId;
    @Column(name = "name" ,unique = true)
    private String name;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "facultad_carreras",
            joinColumns = @JoinColumn(name = "carrera_id"),
            inverseJoinColumns = @JoinColumn(name = "facultad_id")
    )
    private Set<Carrera> carreras = new HashSet<>();

    public Facultad() {
    }

    public Facultad(FacultadDto facultadDto) {
        this.facultadId = facultadDto.facultadId();
        this.name = facultadDto.name();
        this.carreras = facultadDto.carreras().stream().map(CarreraDto::toEntity).collect(Collectors.toSet());
    }

    public Facultad(FacultadRequestDto facultadDto) {
        this.facultadId = facultadDto.facultadId();
        this.name = facultadDto.name();
        this.carreras = new HashSet<>();
    }

    public FacultadDto toDto() {
        return new FacultadDto(this);
    }
}
