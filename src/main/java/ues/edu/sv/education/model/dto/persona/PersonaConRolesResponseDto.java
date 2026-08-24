package ues.edu.sv.education.model.dto.persona;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Persona junto con los papeles clinicos que ya tiene")
public record PersonaConRolesResponseDto(

        Long personaId,
        String dui,
        String nombres,
        String apellidos,
        LocalDate fechaNacimiento,
        String sexo,
        String telefono,
        String direccion,
        String email,

        @Schema(description = "Si ya tiene fila en medicos")
        boolean esMedico,

        @Schema(description = "Si ya tiene fila en enfermeras")
        boolean esEnfermera,

        @Schema(description = "Si ya tiene fila en pacientes")
        boolean esPaciente

) {
}
