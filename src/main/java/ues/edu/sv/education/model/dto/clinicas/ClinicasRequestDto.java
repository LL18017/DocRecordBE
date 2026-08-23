package ues.edu.sv.education.model.dto.clinicas;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos requeridos para registrar una clínica")
public record ClinicasRequestDto(

        @Schema(
                description = "Nombre de la clínica",
                example = "Clínica Regional de Santa Ana"
        )
        @NotBlank(message = "El nombre de la clínica no puede estar vacío")
        @Size(max = 100, message = "El nombre de la clínica no puede superar los 100 caracteres")
        String name,

        @Schema(
                description = "Latitud de la clínica",
                example = "13.9942"
        )
        @NotNull(message = "La latitud es obligatoria")
        Double latitud,

        @Schema(
                description = "Longitud de la clínica",
                example = "-89.5597"
        )
        @NotNull(message = "La longitud es obligatoria")
        Double longitud

        // Sin `userId`: el propietario de una clinica nueva es siempre el
        // usuario autenticado (ver ClinicaService.usuarioActual()), nunca un id
        // que mande el cliente.

) {
}