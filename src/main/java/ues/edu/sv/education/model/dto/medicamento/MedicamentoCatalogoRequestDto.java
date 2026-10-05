package ues.edu.sv.education.model.dto.medicamento;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lo que se pide para registrar o editar un medicamento del catalogo.
 *
 * HU-23 criterio 1: los cinco campos son obligatorios. Ninguno sobra para una
 * receta: sin presentacion no se sabe si es jarabe o tableta, sin
 * concentracion no se sabe cuanto lleva, y sin principio activo HU-24 no
 * puede cruzarlo contra las alergias.
 *
 * NO lleva `activo`: se nace activo, y activar o desactivar es una operacion
 * aparte (PATCH /medicamentos/{id}/estado) para que editar un nombre no pueda
 * sacar un producto de las recetas por descuido.
 */
@Schema(description = "Datos de un medicamento del catalogo")
public record MedicamentoCatalogoRequestDto(

        @Schema(example = "Amoxicilina")
        @NotBlank(message = "El nombre generico es obligatorio")
        @Size(max = 80, message = "El nombre generico no puede superar los 80 caracteres")
        String nombreGenerico,

        @Schema(example = "Amoxil")
        @NotBlank(message = "El nombre comercial es obligatorio")
        @Size(max = 80, message = "El nombre comercial no puede superar los 80 caracteres")
        String nombreComercial,

        @Schema(description = "Si son varios, separados por ' + '", example = "Amoxicilina")
        @NotBlank(message = "El principio activo es obligatorio")
        @Size(max = 150, message = "El principio activo no puede superar los 150 caracteres")
        String principioActivo,

        @Schema(example = "Capsula")
        @NotBlank(message = "La presentacion es obligatoria")
        @Size(max = 50, message = "La presentacion no puede superar los 50 caracteres")
        String presentacion,

        @Schema(example = "500 mg")
        @NotBlank(message = "La concentracion es obligatoria")
        @Size(max = 40, message = "La concentracion no puede superar los 40 caracteres")
        String concentracion

) {
}
