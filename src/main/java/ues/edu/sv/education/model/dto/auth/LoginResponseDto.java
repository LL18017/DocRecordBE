package ues.edu.sv.education.model.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ues.edu.sv.education.model.dto.roles.RoleResponseDto;

import java.util.List;

public record LoginResponseDto(
        @NotNull(message = "El usuario no puede ser nulo")
        @NotNull(message = "El usuario no puede ser nula")
        @NotBlank(message = "El usuario no puede estar vacia")
        String userName,
        @NotNull(message = "El token no puede ser nulo")
        @NotBlank(message = "El token no puede ser nulo")
        String token,
        @NotNull(message = "El token no puede ser nulo")
        @NotBlank(message = "El token no puede ser nulo")
        String refreshToken,

        List<RoleResponseDto> roles

) {
}
