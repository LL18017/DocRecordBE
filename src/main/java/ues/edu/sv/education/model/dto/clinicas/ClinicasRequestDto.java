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

        // ── Dónde queda, de verdad (HU-26 criterio 1) ────────────────────────
        // Obligatorios. La historia los pide para que la clínica «aparezca en el
        // mapa para los pacientes», y un paciente que busca dónde atenderse no
        // puede hacer nada con un nombre y un punto: necesita el municipio para
        // saber si le queda cerca, la dirección para llegar, el teléfono para
        // preguntar y el horario para no ir en balde.

        @Schema(description = "Departamento de El Salvador", example = "Santa Ana")
        @NotBlank(message = "El departamento es obligatorio")
        @Size(max = 40, message = "El departamento no puede superar los 40 caracteres")
        String departamento,

        @Schema(description = "Municipio", example = "Santa Ana")
        @NotBlank(message = "El municipio es obligatorio")
        @Size(max = 60, message = "El municipio no puede superar los 60 caracteres")
        String municipio,

        @Schema(description = "Dirección exacta", example = "Avenida Independencia Sur, Barrio Santa Bárbara")
        @NotBlank(message = "La dirección es obligatoria")
        @Size(max = 200, message = "La dirección no puede superar los 200 caracteres")
        String direccion,

        @Schema(description = "Teléfono de contacto", example = "2440-1234")
        @NotBlank(message = "El teléfono es obligatorio")
        @Size(max = 20, message = "El teléfono no puede superar los 20 caracteres")
        String telefono,

        @Schema(description = "Horario de atención", example = "Lunes a viernes, 7:00 a 16:00")
        @NotBlank(message = "El horario de atención es obligatorio")
        @Size(max = 120, message = "El horario no puede superar los 120 caracteres")
        String horario,

        // ── Las coordenadas ──────────────────────────────────────────────────
        // Siguen siendo OPCIONALES: una clínica se registra mucho antes de que
        // alguien vaya a tomarle el GPS, y exigirlas obligaba a inventar un
        // punto para poder guardar. En un sistema de georreferenciación, una
        // coordenada falsa es peor que una ausente.
        //
        // Pero opcional no es «cualquier cosa», y el rango ya no es el planeta
        // entero. El Salvador va de 13.0 a 14.5 de latitud y de -90.2 a -87.6 de
        // longitud; acotarlo ahí convierte en un rechazo inmediato el error que
        // la propia historia señala: invertir latitud y longitud al capturarlas.
        // Con los valores cambiados de sitio, (13.99, -89.56) se vuelve
        // (-89.56, 13.99) — una latitud que no existe — y el sistema lo dice en
        // vez de dejar la clínica en medio del océano Índico hasta la
        // demostración.
        //
        // El mismo rango está como CHECK en la base (V16): esta validación
        // protege a quien entra por la API, el CHECK también a quien entra por
        // un script o por psql.

        @Schema(
                description = "Latitud. Opcional; si se envía debe caer dentro de El Salvador (13.0 a 14.5)",
                example = "13.9942",
                nullable = true
        )
        @DecimalMin(value = "13.0", message = "La latitud debe estar entre 13.0 y 14.5 (territorio salvadoreño). ¿Se invirtieron latitud y longitud?")
        @DecimalMax(value = "14.5", message = "La latitud debe estar entre 13.0 y 14.5 (territorio salvadoreño). ¿Se invirtieron latitud y longitud?")
        Double latitud,

        @Schema(
                description = "Longitud. Opcional; si se envía debe caer dentro de El Salvador (-90.2 a -87.6)",
                example = "-89.5597",
                nullable = true
        )
        @DecimalMin(value = "-90.2", message = "La longitud debe estar entre -90.2 y -87.6 (territorio salvadoreño). ¿Se invirtieron latitud y longitud?")
        @DecimalMax(value = "-87.6", message = "La longitud debe estar entre -90.2 y -87.6 (territorio salvadoreño). ¿Se invirtieron latitud y longitud?")
        Double longitud

        // Sin `userId`: el propietario de una clínica nueva es siempre el
        // usuario autenticado (ver ClinicaService.usuarioActual()), nunca un id
        // que mande el cliente.
) {
}
