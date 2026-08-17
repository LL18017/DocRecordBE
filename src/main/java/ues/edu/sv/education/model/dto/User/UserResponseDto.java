package ues.edu.sv.education.model.dto.User;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ues.edu.sv.education.model.dto.roles.RoleDto;

import java.util.List;

public record UserResponseDto(
        @NotNull(message = "el id no puede ser nulo")
        @NotBlank(message = "el id no puede estar vacio")
        Integer userId,
        @Email(message = "formato no valido para email")
        @Size(max = 100, message = "el email debe poseer menos de 100 caracteres")
        @NotNull(message = "el correo no puede ser nulo")
        @NotBlank(message = "el correo no puede estar vacio")
        String email,
        @NotNull(message = "El usuario no puede ser nulo")
        @NotNull(message = "El usuario no puede ser nula")
        @NotBlank(message = "El usuario no puede estar vacia")
        String userName,
        List<RoleDto> roles,
        @Email(message = "formato no valido para tipo de usuario")
        @NotNull(message = "el tipo de usuario no puede ser nulo")
        String type) {
}
