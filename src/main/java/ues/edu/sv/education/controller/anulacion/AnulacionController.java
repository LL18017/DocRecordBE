package ues.edu.sv.education.controller.anulacion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.anulacion.ParametroDeAnulacionDto;
import ues.edu.sv.education.model.dto.anulacion.ParametroDeAnulacionRequestDto;
import ues.edu.sv.education.model.dto.anulacion.RechazoDeAnulacionDto;
import ues.edu.sv.education.model.dto.anulacion.SolicitudDeAnulacionDto;
import ues.edu.sv.education.model.enums.EstadoSolicitudDeAnulacion;
import ues.edu.sv.education.model.enums.TipoDocumentoAnulable;
import ues.edu.sv.education.service.anulacion.AnulacionService;

import java.util.List;

/**
 * Solicitudes de anulacion de consultas y recetas, y sus reglas (V35).
 *
 * La anulacion se PIDE en el documento (POST /consultas/{id}/anular,
 * POST /prescripciones/{id}/anular). Aqui se resuelve lo que quedo pendiente
 * y se configura la ventana.
 *
 * ── Quien puede que ────────────────────────────────────────────────────────
 * LEER: ADMIN y MEDICO. El medico ve las suyas, y las de una consulta para
 * saber si hay una anulacion en curso. Enfermeria no anula nada y no entra.
 *
 * APROBAR, RECHAZAR Y CONFIGURAR: solo ADMIN. Y el servicio exige ademas que
 * quien aprueba o rechaza no sea quien pidio la anulacion.
 */
@RestController
@RequestMapping("/anulaciones")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MEDICO')")
@Tag(name = "Anulaciones", description = "Aprobacion de anulaciones de consultas y recetas, y sus parametros")
public class AnulacionController {

    private final AnulacionService anulacionService;

    @Operation(
            summary = "Listar solicitudes de anulacion",
            description = "De la mas reciente a la mas antigua. estado (PENDIENTE, APROBADA, RECHAZADA, CANCELADA) "
                    + "y consultaId son opcionales. Un ADMIN ve todas; un MEDICO, las que pidio el, o todas las de "
                    + "la consulta si manda consultaId."
    )
    @GetMapping
    public ResponseEntity<List<SolicitudDeAnulacionDto>> listar(
            @RequestParam(required = false) EstadoSolicitudDeAnulacion estado,
            @RequestParam(required = false) Long consultaId) {
        return ResponseEntity.ok(anulacionService.listar(estado, consultaId));
    }

    @Operation(summary = "Ver una solicitud de anulacion",
            description = "403 si quien pide no es ADMIN ni quien la pidio; 404 si no existe.")
    @GetMapping("/{solicitudId}")
    public ResponseEntity<SolicitudDeAnulacionDto> obtener(@PathVariable("solicitudId") Long solicitudId) {
        return ResponseEntity.ok(anulacionService.obtener(solicitudId));
    }

    @Operation(
            summary = "Aprobar una solicitud de anulacion",
            description = "Cada administrador aprueba una vez. Con la ultima aprobacion que faltaba, el documento "
                    + "queda ANULADA en el acto (y, si es una consulta, sus recetas vigentes). 403 si no es ADMIN "
                    + "o si es quien la pidio; 409 si ya la aprobo o si la solicitud ya se resolvio; 404 si no existe."
    )
    @PostMapping("/{solicitudId}/aprobar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SolicitudDeAnulacionDto> aprobar(@PathVariable("solicitudId") Long solicitudId) {
        return ResponseEntity.ok(anulacionService.aprobar(solicitudId));
    }

    @Operation(
            summary = "Rechazar una solicitud de anulacion",
            description = "Basta un rechazo; el documento sigue vigente y se puede volver a pedir. Exige motivo "
                    + "(10 a 500 caracteres, 400 si no). 403 si no es ADMIN o si es quien la pidio; 409 si ya se "
                    + "resolvio; 404 si no existe."
    )
    @PostMapping("/{solicitudId}/rechazar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SolicitudDeAnulacionDto> rechazar(
            @PathVariable("solicitudId") Long solicitudId,
            @Valid @RequestBody RechazoDeAnulacionDto request) {
        return ResponseEntity.ok(anulacionService.rechazar(solicitudId, request.motivo()));
    }

    @Operation(
            summary = "Ver los parametros de anulacion",
            description = "Uno por tipo de documento (CONSULTA y RECETA): minutos de anulacion directa, "
                    + "aprobaciones requeridas y tope de dias. El medico los lee para saber de antemano si su "
                    + "anulacion sera directa o necesitara aprobacion."
    )
    @GetMapping("/parametros")
    public ResponseEntity<List<ParametroDeAnulacionDto>> parametros() {
        return ResponseEntity.ok(anulacionService.parametros());
    }

    @Operation(
            summary = "Cambiar los parametros de anulacion de un tipo de documento",
            description = "Solo ADMIN. minutosAnulacionDirecta de 0 a 1440 (0 = siempre con aprobacion), "
                    + "aprobacionesRequeridas de 1 a 5, diasMaximos de 1 a 3650 o null (sin tope). No cambia las "
                    + "solicitudes en curso, que conservan las aprobaciones que pedian."
    )
    @PutMapping("/parametros/{tipo}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ParametroDeAnulacionDto> actualizarParametro(
            @PathVariable("tipo") TipoDocumentoAnulable tipo,
            @Valid @RequestBody ParametroDeAnulacionRequestDto request) {
        return ResponseEntity.ok(anulacionService.actualizarParametro(tipo, request));
    }
}
