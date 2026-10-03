package ues.edu.sv.education.controller.CondicionHereditaria;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.CondicionHereditaria.CondicionHereditariaRequest;
import ues.edu.sv.education.model.dto.CondicionHereditaria.CondicionHereditariaResponse;
import ues.edu.sv.education.model.dto.CondicionHereditaria.CondicionHereditariaUpdateRequest;
import ues.edu.sv.education.service.CondicionHereditaria.CondicionHereditariaService;

import java.util.List;

/**
 * HU-13 · Condiciones hereditarias: enfermedades de la familia del paciente y
 * el parentesco del familiar afectado.
 *
 * ── Quien puede que ────────────────────────────────────────────────────────
 * LEER: ADMIN, MEDICO y ENFERMERA. La enfermera la consulta pero no la
 * modifica (criterio 4).
 *
 * REGISTRAR, EDITAR, ELIMINAR: solo MEDICO.
 *
 * Antes vivia bajo /api/condiciones-hereditarias. El prefijo sobraba: Caddy ya
 * quita /api antes de llegar aqui, asi que el navegador habria tenido que
 * pedir /api/api/condiciones-hereditarias.
 */
@RestController
@RequestMapping("/condiciones-hereditarias")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MEDICO','ENFERMERA')")
@Tag(name = "Condiciones hereditarias", description = "HU-13: enfermedades de la familia del paciente")
public class CondicionHereditariaController {

    private final CondicionHereditariaService condicionHereditariaService;

    @Operation(
            summary = "Registrar una condición hereditaria",
            description = "404 si el paciente no existe; 400 si el parentesco no es de la lista."
    )
    @PostMapping
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<CondicionHereditariaResponse> crear(
            @Valid @RequestBody CondicionHereditariaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(condicionHereditariaService.crear(request));
    }

    @Operation(
            summary = "Condiciones hereditarias de un paciente",
            description = "Ordenadas por parentesco y nombre. Lista vacía si no tiene ninguna; 404 si el paciente no existe."
    )
    @GetMapping
    public ResponseEntity<List<CondicionHereditariaResponse>> listar(@RequestParam Long pacienteId) {
        return ResponseEntity.ok(condicionHereditariaService.listarDelPaciente(pacienteId));
    }

    @Operation(summary = "Ver una condición hereditaria", description = "404 si no existe.")
    @GetMapping("/{id}")
    public ResponseEntity<CondicionHereditariaResponse> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(condicionHereditariaService.obtenerPorId(id));
    }

    @Operation(summary = "Editar una condición hereditaria", description = "Lo que no viene, no cambia. 404 si no existe.")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<CondicionHereditariaResponse> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody CondicionHereditariaUpdateRequest request) {
        return ResponseEntity.ok(condicionHereditariaService.actualizar(id, request));
    }

    @Operation(summary = "Eliminar una condición hereditaria", description = "404 si no existe.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        condicionHereditariaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
