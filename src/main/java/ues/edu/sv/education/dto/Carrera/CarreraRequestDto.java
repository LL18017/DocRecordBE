package ues.edu.sv.education.dto.Carrera;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ues.edu.sv.education.entity.Carrera;

public record CarreraRequestDto(Integer carreraId,
                                @NotBlank(message = "el nombre de la carrera no puede estar vacio")
                                @NotNull(message = "el nombre de la carrera no puede ser nulo")
                                @Size(max = 100, message = "maximo 100 caracteres para el nombre de la carrrera")String name) {
    public CarreraRequestDto(Carrera entity) {
        this(entity.getCarreraId(), entity.getName());
    }
    public Carrera toEntity(){
        return  new Carrera(this.carreraId,this.name());
    }
}
