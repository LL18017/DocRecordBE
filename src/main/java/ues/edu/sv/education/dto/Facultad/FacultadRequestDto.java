package ues.edu.sv.education.dto.Facultad;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import ues.edu.sv.education.dto.Carrera.CarreraDto;
import ues.edu.sv.education.entity.Carrera;
import ues.edu.sv.education.entity.Facultad;

import java.util.List;

public record FacultadRequestDto(Integer facultadId,
                                 @NotNull(message = "El nombre de la facultad no puede ser nulo")
                                 @NotBlank(message = "El nombre de la facultad no puede estar vacio")
                                 @Size(max = 100, message = "maximo 100 caracteres para el nombre de la facultad") String name,
                                 List<CarreraDto> carreras) {
    public FacultadRequestDto(Facultad entity) {
        this(entity.getFacultadId(), entity.getName(), List.of());
    }

    public Facultad toEntity() {
        return new Facultad(this);
    }
}