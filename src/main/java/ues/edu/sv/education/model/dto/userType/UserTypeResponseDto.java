package ues.edu.sv.education.model.dto.userType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ues.edu.sv.education.model.dto.roles.RoleDto;

import java.util.List;

public record UserTypeResponseDto(
        @NotNull(message = "el id no puede ser nulo")
        @NotBlank(message = "el id no puede estar vacio")
        Integer userTypeId,
        @NotNull(message = "El nombre no puede ser nulo")
        @NotNull(message = "El nombre no puede ser nula")
        @NotBlank(message = "El nombre no puede estar vacia")
        String name) {
}
