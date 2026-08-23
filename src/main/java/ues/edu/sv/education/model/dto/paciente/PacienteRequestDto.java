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

        @Schema(description = "Numero de expediente", example = "P-000001")
        @NotBlank(message = "El expediente no puede estar vacio")
        @Size(max = 12, message = "El expediente no puede superar los 12 caracteres")
        String expediente,

        @Schema(description = "Tipo de sangre", example = "O+")
        @Size(max = 3, message = "El tipo de sangre no puede superar los 3 caracteres")
        String tipoSangre

) {
}
