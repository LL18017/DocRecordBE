package ues.edu.sv.education.controller.prescripcion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.common.PaginaDto;
import ues.edu.sv.education.model.dto.prescripcion.AlertaDeAlergiaDto;
import ues.edu.sv.education.model.dto.prescripcion.DecisionDeAlertaDto;
import ues.edu.sv.education.model.dto.prescripcion.AnulacionRequestDto;
import ues.edu.sv.education.model.dto.prescripcion.PrescripcionRequestDto;
import ues.edu.sv.education.model.dto.prescripcion.PrescripcionResponseDto;
import ues.edu.sv.education.service.prescripcion.PrescripcionService;

import java.time.LocalDate;
import java.util.List;

/**
 * Prescripciones (epica E7).
 * <p>
 * ── Quien puede que ────────────────────────────────────────────────────────
 * LEER: ADMIN, MEDICO y ENFERMERA. Enfermeria es justamente quien administra
 * lo recetado; una receta que la enfermera no puede leer no sirve de nada.
 * <p>
 * PRESCRIBIR: solo MEDICO. Aqui SI se deja fuera al ADMIN, a diferencia de las
 * consultas, y es deliberado: recetar no es un acto administrativo sino la
 * emision de un documento con responsable legal, y no existe "recetar en
 * nombre de". Un administrador que ademas ejerce tiene su rol MEDICO y entra
 * por ahi; uno que no ejerce no tiene nada que firmar. Aunque se colara, el
 * servicio exige fila en `medicos` para poner la firma.
 * <p>
 * ANULAR: ADMIN, o el MEDICO que la firmo. Una receta mal capturada hay que
 * poder invalidarla, pero no borrarla ni editarla: se anula con motivo y se
 * emite otra (HU-22). El DELETE fisico que habia aqui se retiro.
 */
@Slf4j
@RestController
@RequestMapping("/prescripciones")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MEDICO','ENFERMERA')")
@Tag(name = "Prescripciones", description = "Recetas emitidas a partir de una consulta")
public class PrescripcionController {

    private final PrescripcionService prescripcionService;

    @Operation(
            summary = "Emitir una receta",
            description = "La receta hereda de la consulta paciente, medico, clinica y fecha, y solo la emite "
                    + "el medico de esa consulta (403 en otro caso). Cada renglon exige dosis, "
                    + "viaAdministracion, frecuencia y duracion (400 si falta alguna). "
                    + "Cada medicamento se elige del catalogo por su medicamentoId (GET /medicamentos). "
                    + "400 si la lista de medicamentos viene vacia o si un medicamento no existe en el "
                    + "catalogo o esta desactivado; 404 si la consulta no existe. 409 si algun medicamento "
                    + "coincide con una alergia vigente del paciente y falta la justificacionAlergia, o "
                    + "la alergia es SEVERA y confirmaAlergiaSevera no es true (HU-24)."
    )
    @PostMapping
    @PreAuthorize("hasAnyRole('MEDICO','ADMIN')")
    public ResponseEntity<PrescripcionResponseDto> crear(
            @Valid @RequestBody PrescripcionRequestDto request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(prescripcionService.crear(request));
    }

    @Operation(
            summary = "Alertas de alergia de un medicamento (HU-24)",
            description = "Las alergias vigentes del paciente (pacienteId = persona_id) que coinciden con "
                    + "el medicamento, por contencion de texto normalizado entre la sustancia y el "
                    + "principio activo o el nombre generico. Lista vacia si no hay ninguna. "
                    + "404 si el paciente o el medicamento no existen."
    )
    @GetMapping("/alertas-de-alergia")
    @PreAuthorize("hasAnyRole('MEDICO','ADMIN')")
    public ResponseEntity<List<AlertaDeAlergiaDto>> alertasDeAlergia(
            @RequestParam Long pacienteId,
            @RequestParam Long medicamentoId) {
        return ResponseEntity.ok(prescripcionService.alertasDeAlergia(pacienteId, medicamentoId));
    }

    @Operation(
            summary = "Registrar la decision ante una alerta de alergia (HU-24, criterio 5)",
            description = "El medico vio la alerta y desistio de recetar: queda en la bitacora (evento "
                    + "ALLERGY_ALERT_DECLINED) con la alergia mostrada y la decision. Si en cambio receta "
                    + "pese a la alerta, el evento lo escribe POST /prescripciones. Quien decide sale del "
                    + "token. 403 si no es medico; 404 si el paciente o el medicamento no existen; 409 si "
                    + "no hay alerta vigente para ese par."
    )
    @PostMapping("/alertas-de-alergia/decision")
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<Void> decisionDeAlerta(@Valid @RequestBody DecisionDeAlertaDto decision) {
        prescripcionService.registrarDecisionDeAlerta(decision);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Emitir una receta",
            description = "Firma el medico autenticado, no un medicoId del cuerpo. "
                    + "400 si la lista de medicamentos esta vacia; 404 si la consulta no existe."
    )
    @PostMapping("/enviar-receta/{consultaId}")
    @PreAuthorize("hasAnyRole('MEDICO','ADMIN')")
    public ResponseEntity<?> enviarReceta(
            @Valid @PathVariable  Long consultaId) {
        prescripcionService.enviarReceta(consultaId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Listar recetas",
            description = "Historico de recetas, paginado. pacienteId, medicoId, consultaId, desde y "
                    + "hasta son TODOS opcionales y COMBINABLES entre si (p. ej. pacienteId y "
                    + "medicoId a la vez). Sin ningun filtro devuelve el historico completo, "
                    + "paginado, de la receta mas reciente a la mas antigua. desde/hasta son "
                    + "fechas (yyyy-MM-dd); hasta incluye el dia completo (hasta las 23:59:59.999 "
                    + "de ese dia). Un pacienteId/medicoId/consultaId que no exista no da 404: da "
                    + "una pagina vacia, porque aqui son filtros de una busqueda, no la "
                    + "identificacion de un recurso unico (ver PrescripcionService.listar)."
    )
    @GetMapping
    public ResponseEntity<PaginaDto<PrescripcionResponseDto>> listar(
            @RequestParam(required = false) Long consultaId,
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) Long medicoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false, defaultValue = "0") Integer pagina,
            @RequestParam(required = false, defaultValue = "20") Integer tamano
    ) {
        return ResponseEntity.ok(prescripcionService.listar(
                consultaId, pacienteId, medicoId, desde, hasta, pagina, tamano));
    }

    @Operation(summary = "Ver una receta", description = "404 si no existe.")
    @GetMapping("/{prescripcionId}")
    public ResponseEntity<PrescripcionResponseDto> obtener(
            @PathVariable("prescripcionId") Long prescripcionId) {
        return ResponseEntity.ok(prescripcionService.obtener(prescripcionId));
    }

    @Operation(
            summary = "Imprimir una receta (PDF)",
            description = "Descarga la receta en PDF con el medico y su numero de junta de vigilancia, los "
                    + "datos de la clinica, el paciente y los medicamentos. Una receta anulada tambien se "
                    + "descarga, marcada como anulada. 404 si no existe."
    )
    @GetMapping(value = "/{prescripcionId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable("prescripcionId") Long prescripcionId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"receta-" + prescripcionId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(prescripcionService.generarPdf(prescripcionId));
    }

    @Operation(
            summary = "Anular una receta",
            description = "Anulacion logica (HU-22): la receta pasa a ANULADA con el motivo, quien y cuando, y "
                    + "sigue visible, marcada. NO se borra ni se edita; para corregirla se anula y se emite "
                    + "otra. Solo un ADMIN o el medico que la firmo (403 en otro caso). 409 si ya estaba "
                    + "anulada; 400 si falta el motivo (10 a 500 caracteres); 404 si no existe."
    )
    @PostMapping("/{prescripcionId}/anular")
    @PreAuthorize("hasAnyRole('ADMIN','MEDICO')")
    public ResponseEntity<PrescripcionResponseDto> anular(
            @PathVariable("prescripcionId") Long prescripcionId,
            @Valid @RequestBody AnulacionRequestDto request) {
        return ResponseEntity.ok(prescripcionService.anular(prescripcionId, request.motivo()));
    }
}
