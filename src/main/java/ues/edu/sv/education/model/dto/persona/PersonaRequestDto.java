package ues.edu.sv.education.model.dto.persona;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Datos de persona para crear o completar dentro de un alta de paciente")
public record PersonaRequestDto(

        @Schema(description = "Si viene, se completa esa persona existente en vez de crear una nueva", example = "42")
        Long personaId,

        @Schema(description = "DUI", example = "04871239-5")
        @Size(max = 10, message = "El DUI no puede superar los 10 caracteres")
        String dui,

        @Schema(description = "Nombres. Obligatorio si personaId no viene", example = "María Elena")
        @Size(max = 80, message = "Los nombres no pueden superar los 80 caracteres")
        String nombres,

        @Schema(description = "Apellidos. Obligatorio si personaId no viene", example = "López Torres")
        @Size(max = 80, message = "Los apellidos no pueden superar los 80 caracteres")
        String apellidos,

        @Schema(description = "Fecha de nacimiento", example = "1991-03-14")
        LocalDate fechaNacimiento,

        @Schema(description = "Sexo: M o F", example = "F")
        @Pattern(regexp = "[MF]", message = "El sexo debe ser M o F")
        String sexo,

        @Schema(description = "Telefono salvadoreño de 8 digitos, con o sin guion", example = "2222-2222")
        @Pattern(regexp = "^(\\d{4}-?\\d{4})?$",
                message = "El telefono debe tener 8 digitos (2222-2222 o 22222222)")
        String telefono,

        @Schema(description = "Direccion")
        @Size(max = 255, message = "La direccion no puede superar los 255 caracteres")
        String direccion,

        @Schema(description = "Correo de contacto")
        @Email(message = "El correo no tiene un formato valido")
        @Size(max = 255, message = "El correo no puede superar los 255 caracteres")
        String email

) {

    private static final java.time.ZoneId ZONA = java.time.ZoneId.of("America/El_Salvador");

    /**
     * El telefono en la forma en que se guarda, NNNN-NNNN, o null si no vino.
     * Los tests y los datos existentes lo usan con guion; "22222222" y
     * "2222-2222" son el mismo numero y no deben guardarse como dos cosas.
     */
    public String telefonoNormalizado() {
        if (telefono == null || telefono.isBlank()) return null;
        String digitos = telefono.replace("-", "");
        return digitos.substring(0, 4) + "-" + digitos.substring(4);
    }

    /** Nadie nace manana: la fecha de hoy se toma en la zona del pais, no en la del servidor (UTC). */
    public boolean fechaNacimientoEsFutura() {
        return fechaNacimiento != null && fechaNacimiento.isAfter(java.time.LocalDate.now(ZONA));
    }
}
