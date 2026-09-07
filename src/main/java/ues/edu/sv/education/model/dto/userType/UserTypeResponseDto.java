package ues.edu.sv.education.model.dto.userType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserTypeResponseDto(
        @NotNull(message = "el id no puede ser nulo")
        @NotBlank(message = "el id no puede estar vacio")
        Integer userTypeId,
        @NotNull(message = "El nombre no puede ser nulo")
        @NotNull(message = "El nombre no puede ser nula")
        @NotBlank(message = "El nombre no puede estar vacia")
        String name) {
}
