package ues.edu.sv.education.model.dto.medico;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * La ficha del medico (HU-02): quien es como profesional, sin cuenta.
 *
 * La cuenta para entrar es otra cosa y se da despues, desde la ficha
 * (POST /medicos/{id}/acceso). Separarlas es lo que permite que un medico
 * exista aunque nunca entre al sistema, y que desactivar su cuenta no toque
 * los datos con que firma sus consultas.
 */
@Schema(description = "Ficha del medico: datos personales y profesionales")
public record MedicoRequestDto(

        @Schema(example = "Lucía")
        @NotBlank(message = "Los nombres son obligatorios")
        @Size(max = 80, message = "Los nombres no pueden superar los 80 caracteres")
        String nombres,

        @Schema(example = "Mejía Ramos")
        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(max = 80, message = "Los apellidos no pueden superar los 80 caracteres")
        String apellidos,

        @Schema(description = "Telefono de 8 digitos, con o sin guion; opcional", example = "7777-0000")
        @Pattern(regexp = "^(\\d{4}-?\\d{4})?$", message = "El telefono debe tener 8 digitos (2222-2222 o 22222222)")
        String telefono,

        @Schema(example = "1")
        @NotNull(message = "La especialidad es obligatoria")
        Long especialidadId,

        // HU-02 c1. El formato es basico a proposito -- 3 a 20 letras, numeros
        // o guiones, con al menos un numero--: la Junta no publica un patron
        // unico (JVPM-1234, 12345, JVPM-ENF-4471 conviven) y rechazar uno
        // legitimo es peor que dejar pasar uno raro. Se guarda en mayusculas.
        @Schema(description = "Numero de registro en la Junta de Vigilancia de la Profesion Medica", example = "JVPM-12345")
        @NotBlank(message = "El número de junta de vigilancia es obligatorio")
        @Pattern(regexp = "^\\s*(?=\\S*\\d)[A-Za-z0-9-]{3,20}\\s*$",
                message = "El número de junta de vigilancia no tiene un formato válido: use de 3 a 20 letras, números o guiones, con al menos un número")
        String registroJunta,

        @Schema(description = "Solo al crear, y opcional: la cuenta YA existente de esta persona (por "
                + "ejemplo, un administrador que tambien ejerce). Con ella, la ficha se cuelga de su "
                + "persona en vez de crear otra, y nombres y apellidos se toman de ahi.", example = "12")
        Integer userId

) {
}
