package ues.edu.sv.education.model.dto.paciente;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ues.edu.sv.education.model.dto.persona.PersonaRequestDto;

@Schema(description = "Datos para registrar un paciente")
public record PacienteRequestDto(

        @Schema(description = "Persona duena del expediente. Con personaId se completa una existente; sin el, se crea una nueva")
        @NotNull(message = "La persona es obligatoria")
        @Valid
        PersonaRequestDto persona,


        @Schema(description = "Tipo de sangre", example = "O+")
        @Size(max = 3, message = "El tipo de sangre no puede superar los 3 caracteres")
        String tipoSangre,

        // Opcional. La sede activa de quien registra: el paciente guarda en
        // que clinica se dio de alta (HU-07 criterio 4). Solo se usa en el
        // alta; quien registra sale del token, nunca del cuerpo.
        @Schema(description = "ID de la clinica (sede activa) donde se registra al paciente", example = "1")
        Integer clinicaId

) {
}
