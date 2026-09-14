package ues.edu.sv.education.model.dto.signosvitales;

import io.swagger.v3.oas.annotations.media.Schema;
import ues.edu.sv.education.model.dto.consulta.PacienteResumenDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una toma de constantes, como la lee el medico.
 *
 * Las medidas salen con el mismo tipo con que se guardaron, sin unidad pegada
 * y sin formatear: componer "120/80 mmHg" es decision de quien pinta la
 * pantalla, no del backend. Un cliente que reciba el par ya concatenado no
 * puede graficar la tension ni comparar dos tomas.
 *
 * Los nulos viajan como nulos, no como cero ni como cadena vacia. La
 * diferencia entre "no se tomo la saturacion" y "la saturacion es 0" es la
 * diferencia entre un hueco y una urgencia.
 */
@Schema(description = "Toma de constantes registrada por enfermeria")
public record SignosVitalesResponseDto(

        @Schema(example = "5") Long signosVitalesId,

        LocalDateTime tomadoEn,

        PacienteResumenDto paciente,

        EnfermeraTomaDto enfermera,

        @Schema(description = "Consulta a la que quedo ligada, si hay alguna", example = "12", nullable = true)
        Long consultaId,

        @Schema(example = "72.50", nullable = true) BigDecimal pesoKg,
        @Schema(example = "175.00", nullable = true) BigDecimal estaturaCm,
        @Schema(example = "36.8", nullable = true) BigDecimal temperaturaC,

        @Schema(example = "120", nullable = true) Short presionSistolica,
        @Schema(example = "80", nullable = true) Short presionDiastolica,

        @Schema(example = "78", nullable = true) Short pulsoLpm,
        @Schema(example = "16", nullable = true) Short frecuenciaRespRpm,
        @Schema(example = "98", nullable = true) Short saturacionPct,

        @Schema(nullable = true) String observaciones,

        /**
         * IMC = peso / talla², con un decimal (HU-17 criterio 1).
         *
         * Null cuando falta el peso o la talla. Un IMC calculado con uno de los
         * dos ausente no es un IMC aproximado: es un numero inventado.
         *
         * Se calcula al leer y no se guarda en una columna a proposito: es un
         * valor DERIVADO de otros dos que si estan en la fila, y guardarlo
         * abriria la posibilidad de que quede desincronizado -- un peso
         * corregido y un IMC que sigue diciendo lo de antes.
         */
        @Schema(example = "23.7", nullable = true) BigDecimal imc,

        /**
         * La clasificacion de la OMS, o el aviso de que no aplica.
         *
         * En menores de 18 anos NO dice "Normal" ni "Sobrepeso": dice que hay
         * que interpretarlo por percentiles (HU-17 criterio 2). La tabla de
         * adultos aplicada a un nino da una etiqueta sin sentido, y una etiqueta
         * equivocada en un expediente orienta decisiones.
         */
        @Schema(example = "Normal", nullable = true) String clasificacionImc

) {
}
