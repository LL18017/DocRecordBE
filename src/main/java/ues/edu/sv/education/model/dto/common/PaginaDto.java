package ues.edu.sv.education.model.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Respuesta paginada generica, para cualquier listado del proyecto que lo
 * necesite.
 *
 * ── Decision de forma (ver PrescripcionService.listar) ───────────────────
 * En el proyecto ya existe una trampa de este tipo: GET /user/all recibe
 * inicio y fin, y por dentro hace PageRequest.of(inicio, fin) -- eso es
 * PAGINA y TAMANO de pagina, no un rango de filas -- y UserService.getAll
 * devuelve solo un List<> plano. Una llamada sin cuidado (alguien que pasa
 * fin=20 pensando "hasta la fila 20") se queda con las primeras 20 filas y
 * nada en la respuesta le avisa que falta el resto.
 *
 * Este DTO existe para que eso no se pueda repetir: viajan siempre el
 * contenido de la pagina Y el total, en la misma respuesta, asi que el
 * cliente nunca tiene que adivinar si le falta algo.
 */
@Schema(description = "Pagina de resultados: trae el total junto con el contenido, "
        + "para que el cliente nunca tenga que adivinar si le falta algo")
public record PaginaDto<T>(

        @Schema(description = "Los elementos de ESTA pagina, no el listado completo")
        List<T> contenido,

        @Schema(description = "Pagina actual, base 0", example = "0")
        int paginaActual,

        @Schema(description = "Elementos por pagina solicitados", example = "20")
        int tamanoPagina,

        @Schema(description = "Total de elementos que cumplen el filtro, sumando TODAS las paginas", example = "137")
        long totalElementos,

        @Schema(description = "Total de paginas disponibles con este tamanoPagina", example = "7")
        int totalPaginas

) {
    public static <T> PaginaDto<T> de(Page<T> pagina) {
        return new PaginaDto<>(
                pagina.getContent(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages()
        );
    }
}
