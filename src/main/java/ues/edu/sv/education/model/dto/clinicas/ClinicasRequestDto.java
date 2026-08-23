package ues.edu.sv.education.model.dto.clinicas;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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

        // Las coordenadas son OPCIONALES, igual que las columnas clinicas.latitud
        // y clinicas.longitud, que admiten NULL. Una clinica se registra por su
        // nombre mucho antes de que alguien vaya a tomarle el GPS; exigirlas
        // obligaba a inventar coordenadas para poder guardar, y en un sistema de
        // georreferenciacion un dato falso es peor que un dato ausente.
        //
        // Opcional no es "cualquier cosa": si la coordenada viene, tiene que ser
        // una coordenada. Fuera de -90..90 / -180..180 no existe punto en la
        // Tierra, asi que se rechaza con 400 en vez de guardarse.
        @Schema(
                description = "Latitud de la clínica. Opcional; si se envía debe estar entre -90 y 90",
                example = "13.9942",
                nullable = true
        )
        @DecimalMin(value = "-90.0", message = "La latitud debe estar entre -90 y 90")
        @DecimalMax(value = "90.0", message = "La latitud debe estar entre -90 y 90")
        Double latitud,

        @Schema(
                description = "Longitud de la clínica. Opcional; si se envía debe estar entre -180 y 180",
                example = "-89.5597",
                nullable = true
        )
        @DecimalMin(value = "-180.0", message = "La longitud debe estar entre -180 y 180")
        @DecimalMax(value = "180.0", message = "La longitud debe estar entre -180 y 180")
        Double longitud

        // Sin `userId`: el propietario de una clinica nueva es siempre el
        // usuario autenticado (ver ClinicaService.usuarioActual()), nunca un id
        // que mande el cliente.

) {
}
