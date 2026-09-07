package ues.edu.sv.education.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserLoginDto(
        @Email(message = "formato no valido para email")
        @Size(max = 100, message = "el email debe poseer menos de 100 caracteres")
        @NotNull(message = "el correo no puede ser nulo")
        @NotBlank(message = "el correo no puede estar vacio")
        @Schema(description = "Correo electrónico del usuario", example = "mario992lopez@gmail.com")
        String email,
        @NotNull(message = "la contraseña no puede ser nula")
        @NotNull(message = "la contraseña no puede ser nula")
        @NotBlank(message = "la contraseña no puede estar vacia")
        @Schema(description = "contraseña", example = "12345")
        String password) {
}
