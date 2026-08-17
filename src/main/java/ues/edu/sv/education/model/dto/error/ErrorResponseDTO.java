package ues.edu.sv.education.model.dto.error;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ErrorResponseDTO(
        @NotNull(message = "El detalle de el error no puede ser nulo")
        @NotBlank(message = "El detalle de el error no puede estar vacio")
        String error,
        @NotNull(message = "El codigo de el error no puede ser nulo")
        @NotBlank(message = "El codigo de el error no puede estar vacio")
        int code
) {
}
