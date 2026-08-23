package ues.edu.sv.education.controller.prescripcion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.prescripcion.PrescripcionRequestDto;
import ues.edu.sv.education.model.dto.prescripcion.PrescripcionResponseDto;
import ues.edu.sv.education.service.prescripcion.PrescripcionService;

import java.util.List;

/**
 * Prescripciones (epica E7).
 *
 * ── Quien puede que ────────────────────────────────────────────────────────
 * LEER: ADMIN, MEDICO y ENFERMERA. Enfermeria es justamente quien administra
 * lo recetado; una receta que la enfermera no puede leer no sirve de nada.
 *
 * PRESCRIBIR: solo MEDICO. Aqui SI se deja fuera al ADMIN, a diferencia de las
 * consultas, y es deliberado: recetar no es un acto administrativo sino la
 * emision de un documento con responsable legal, y no existe "recetar en
 * nombre de". Un administrador que ademas ejerce tiene su rol MEDICO y entra
 * por ahi; uno que no ejerce no tiene nada que firmar. Aunque se colara, el
 * servicio exige fila en `medicos` para poner la firma.
 *
 * ANULAR: ADMIN o MEDICO. Una receta mal capturada hay que poder quitarla, y
 * eso si es correccion de registro.
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
            description = "Firma el medico autenticado, no un medicoId del cuerpo. "
                    + "400 si la lista de medicamentos viene vacia; 404 si la consulta no existe."
    )
    @PostMapping
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<PrescripcionResponseDto> crear(
            @Valid @RequestBody PrescripcionRequestDto request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(prescripcionService.crear(request));
    }

    @Operation(
            summary = "Listar recetas",
            description = "Por consultaId (las de esa consulta) o por pacienteId (todas las del paciente). "
                    + "Hay que indicar uno de los dos."
    )
    @GetMapping
    public ResponseEntity<List<PrescripcionResponseDto>> listar(
            @RequestParam(required = false) Long consultaId,
            @RequestParam(required = false) Long pacienteId
    ) {
        return ResponseEntity.ok(prescripcionService.listar(consultaId, pacienteId));
    }

    @Operation(summary = "Ver una receta", description = "404 si no existe.")
    @GetMapping("/{prescripcionId}")
    public ResponseEntity<PrescripcionResponseDto> obtener(
            @PathVariable("prescripcionId") Long prescripcionId) {
        return ResponseEntity.ok(prescripcionService.obtener(prescripcionId));
    }

    @Operation(summary = "Anular una receta", description = "Borra la receta con todos sus medicamentos.")
    @DeleteMapping("/{prescripcionId}")
    @PreAuthorize("hasAnyRole('ADMIN','MEDICO')")
    public ResponseEntity<Void> eliminar(@PathVariable("prescripcionId") Long prescripcionId) {
        prescripcionService.eliminar(prescripcionId);
        return ResponseEntity.noContent().build();
    }
}
