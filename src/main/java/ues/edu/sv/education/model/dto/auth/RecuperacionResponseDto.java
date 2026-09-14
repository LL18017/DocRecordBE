package ues.edu.sv.education.model.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * La respuesta de los dos pasos de la recuperacion: un mensaje y nada mas.
 *
 * Un solo campo, y ese campo es texto fijo. HU-04 criterio 4 exige que pedir
 * recuperacion para un correo registrado y para uno que no existe se responda
 * EXACTAMENTE igual; cualquier dato que dependa de la cuenta -un id, un
 * "enviado a j***@ues.edu.sv", una marca de tiempo- seria justo lo que delata
 * cuales existen.
 */
@Schema(description = "Mensaje para mostrarle a quien hizo la solicitud")
public record RecuperacionResponseDto(

        @Schema(description = "Texto a mostrar tal cual")
        String mensaje

) {
}
