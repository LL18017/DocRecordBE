package ues.edu.sv.education.model.dto.User;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UserRequestDto(
        @Email(message = "formato no valido para email")
        @Size(max = 100, message = "el email debe poseer menos de 100 caracteres")
        @NotNull(message = "el correo no puede ser nulo")
        @NotBlank(message = "el correo no puede estar vacio")
        String email,
        @NotNull(message = "El usuario no puede ser nulo")
        @NotNull(message = "El usuario no puede ser nula")
        @NotBlank(message = "El usuario no puede estar vacia")
        String userName,
        @NotNull(message = "la contraseña no puede ser nula")
        @NotNull(message = "la contraseña no puede ser nula")
        @NotBlank(message = "la contraseña no puede estar vacia")
        String password,
        List<Integer> roles
) {
}
