package ues.edu.sv.education.model.dto.userType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserTypeRequestDto(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String name

) {
}