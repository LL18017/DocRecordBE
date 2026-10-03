package ues.edu.sv.education.model.dto.User;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Contrasena nueva que un administrador asigna a otro usuario")
public record AsignarContrasenaRequestDto(

        @Schema(description = "Contrasena en texto plano. Se cifra antes de guardarla y nunca se "
                + "registra ni se devuelve.", example = "NuevaClave2026!")
        @NotBlank(message = "La contrasena no puede estar vacia")
        String password

) {
}
