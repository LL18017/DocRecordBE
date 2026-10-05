package ues.edu.sv.education.controller.medicamento;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.model.dto.medicamento.CambiarEstadoMedicamentoRequestDto;
import ues.edu.sv.education.model.dto.medicamento.MedicamentoCatalogoRequestDto;
import ues.edu.sv.education.model.dto.medicamento.MedicamentoCatalogoResponseDto;
import ues.edu.sv.education.service.medicamento.MedicamentoService;

import java.util.List;

/**
 * Catalogo de medicamentos (HU-23, DRS-92).
 *
 * ── Quien puede que ────────────────────────────────────────────────────────
 * LEER: ADMIN, MEDICO y ENFERMERA. El medico lo necesita para recetar (es el
 * autocompletado de la receta) y enfermeria para saber que es lo que
 * administra.
 *
 * ESCRIBIR (alta, edicion, activar/desactivar): solo ADMIN. Es lo que pide la
 * historia -- "como administrador quiero mantener un catalogo" -- y es lo que
 * hace que la lista sea "controlada": si cada medico pudiera agregar a ella
 * lo que quisiera, el catalogo volveria a ser texto libre con otro nombre.
 *
 * No hay DELETE: un medicamento que ya se receto no puede desaparecer
 * (criterio 4). Se desactiva.
 */
@RestController
@RequestMapping("/medicamentos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MEDICO','ENFERMERA')")
@Tag(name = "Medicamentos", description = "Catalogo de medicamentos desde el que se receta")
public class MedicamentoController {

    private final MedicamentoService medicamentoService;

    @Operation(
            summary = "Listar el catalogo",
            description = "Por defecto solo los medicamentos ACTIVOS, que son los que se pueden recetar. "
                    + "buscar filtra por nombre generico, nombre comercial o principio activo, sin "
                    + "distinguir mayusculas ni tildes. incluirInactivos=true trae tambien los "
                    + "desactivados y solo lo puede pedir un ADMIN (403 para los demas)."
    )
    @GetMapping
    public ResponseEntity<List<MedicamentoCatalogoResponseDto>> listar(
            @RequestParam(required = false) String buscar,
            @RequestParam(required = false, defaultValue = "false") boolean incluirInactivos,
            Authentication autenticacion
    ) {
        // Se rechaza en vez de ignorarse: si se ignorara, un cliente que pide
        // los inactivos recibiria una lista sin ellos y la leeria como "no hay
        // ninguno desactivado", que es falso.
        if (incluirInactivos && !esAdmin(autenticacion)) {
            throw new GeneralException(
                    "Solo un administrador puede ver los medicamentos desactivados", "403");
        }
        return ResponseEntity.ok(medicamentoService.listar(buscar, incluirInactivos));
    }

    @Operation(summary = "Ver un medicamento", description = "404 si no existe. Devuelve tambien los inactivos.")
    @GetMapping("/{medicamentoId}")
    public ResponseEntity<MedicamentoCatalogoResponseDto> obtener(@PathVariable("medicamentoId") Long medicamentoId) {
        return ResponseEntity.ok(medicamentoService.obtener(medicamentoId));
    }

    @Operation(
            summary = "Registrar un medicamento",
            description = "Los cinco campos son obligatorios (400). 409 si ya existe otro con el mismo "
                    + "nombre comercial, presentacion y concentracion -sin distinguir mayusculas, "
                    + "tildes ni espacios-; el mensaje dice cual es."
    )
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MedicamentoCatalogoResponseDto> crear(
            @Valid @RequestBody MedicamentoCatalogoRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(medicamentoService.crear(request));
    }

    @Operation(
            summary = "Editar un medicamento",
            description = "Mismas reglas que el alta. Las recetas ya emitidas no cambian: guardan el "
                    + "nombre que tenia el medicamento al recetarse."
    )
    @PutMapping("/{medicamentoId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MedicamentoCatalogoResponseDto> actualizar(
            @PathVariable("medicamentoId") Long medicamentoId,
            @Valid @RequestBody MedicamentoCatalogoRequestDto request) {
        return ResponseEntity.ok(medicamentoService.actualizar(medicamentoId, request));
    }

    @Operation(
            summary = "Activar o desactivar un medicamento",
            description = "No borra nada. Un medicamento desactivado deja de ofrecerse y de aceptarse en "
                    + "recetas nuevas, pero sigue apareciendo en las recetas emitidas antes."
    )
    @PatchMapping("/{medicamentoId}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MedicamentoCatalogoResponseDto> cambiarEstado(
            @PathVariable("medicamentoId") Long medicamentoId,
            @Valid @RequestBody CambiarEstadoMedicamentoRequestDto request) {
        return ResponseEntity.ok(medicamentoService.cambiarEstado(medicamentoId, request.activo()));
    }

    private static boolean esAdmin(Authentication autenticacion) {
        return autenticacion != null && autenticacion.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
