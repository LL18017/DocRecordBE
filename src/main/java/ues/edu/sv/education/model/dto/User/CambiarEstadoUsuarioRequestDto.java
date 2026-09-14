package ues.edu.sv.education.model.dto.User;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Si la cuenta queda activa o no (HU-05 criterio 3).
 *
 * Un booleano y no dos endpoints (/activar, /desactivar): la operacion es la
 * misma y lo que cambia es el valor, asi que un solo camino evita que las dos
 * mitades se separen con el tiempo.
 */
@Schema(description = "Nuevo estado de la cuenta")
public record CambiarEstadoUsuarioRequestDto(

        @Schema(description = "true deja la cuenta habilitada para iniciar sesion",
                example = "false")
        @NotNull(message = "Hay que indicar si la cuenta queda activa")
        Boolean activo
) {
}
