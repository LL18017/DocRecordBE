package ues.edu.sv.education.model.dto.clinicas;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Una clinica tal como la ve cualquiera en el mapa publico de la red (HU-28).
 *
 * ── Por que un DTO aparte y no ClinicasResponseDto ────────────────────────
 * Este se sirve SIN sesion, a cualquiera en internet. Reutilizar el DTO del
 * portal ataria lo que se publica a lo que necesita la administracion: el dia
 * que alguien le agregue al DTO interno el dueño, el personal asignado o un
 * dato de auditoria, saldria publicado sin que nadie lo decidiera. Con un
 * record propio, publicar un campo nuevo es una decision explicita que se
 * toma aqui.
 *
 * Tampoco lleva `estado`: el endpoint solo devuelve clinicas ACTIVAS, asi que
 * el campo seria siempre el mismo valor y solo revelaria que existen las
 * otras.
 *
 * Los nombres de los campos son los mismos que en ClinicasResponseDto para
 * que el frontend no tenga dos vocabularios para la misma clinica.
 */
@Schema(description = "Clínica activa de la red, con lo necesario para ubicarla en el mapa público")
public record ClinicaPublicaDto(

        @Schema(description = "ID de la clínica", example = "1")
        Integer clinicaId,

        @Schema(description = "Nombre de la clínica", example = "Clínica Regional de Santa Ana")
        String name,

        @Schema(description = "Latitud; null si aún no se le tomó la ubicación", example = "13.9942", nullable = true)
        Double latitud,

        @Schema(description = "Longitud; null si aún no se le tomó la ubicación", example = "-89.5597", nullable = true)
        Double longitud,

        @Schema(description = "Departamento", example = "Santa Ana", nullable = true)
        String departamento,

        @Schema(description = "Municipio", example = "Santa Ana", nullable = true)
        String municipio,

        @Schema(description = "Dirección exacta", nullable = true)
        String direccion,

        @Schema(description = "Teléfono de contacto", nullable = true)
        String telefono,

        @Schema(description = "Horario de atención", nullable = true)
        String horario

) {
}
