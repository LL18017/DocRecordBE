package ues.edu.sv.education.model.dto.enfermera;

import io.swagger.v3.oas.annotations.media.Schema;
import ues.edu.sv.education.model.dto.persona.PersonaResponseDto;

/**
 * Una enfermera registrada.
 *
 * Trae `userId` y `emailDeAcceso` para que quien acaba de darla de alta pueda
 * comprobar que la cuenta quedo creada, y para poder reasignarle la contrasena
 * despues con POST /user/{userId}/password sin tener que ir a buscar el id a
 * otro listado.
 *
 * NUNCA la contrasena, ni en claro ni cifrada: lo que se responde a un alta
 * termina en los registros del cliente y en las capturas de pantalla de quien
 * la hizo.
 */
@Schema(description = "Personal de enfermeria registrado")
public record EnfermeraResponseDto(

        @Schema(example = "10") Long personaId,

        PersonaResponseDto persona,

        @Schema(example = "JVPM-ENF-4471", nullable = true) String registroJunta,

        @Schema(description = "Si sigue en servicio", example = "true") boolean activo,

        @Schema(description = "Id de su cuenta de acceso", example = "12") Integer userId,

        @Schema(example = "marta.guevara@ues.edu.sv") String emailDeAcceso

) {
}
