package ues.edu.sv.education.controller.antecedente;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.antecedente.AntecedentePatologicoRequestDto;
import ues.edu.sv.education.model.dto.antecedente.AntecedentePatologicoResponseDto;
import ues.edu.sv.education.model.dto.antecedente.AntecedentePatologicoUpdateDto;
import ues.edu.sv.education.service.antecedente.AntecedentePatologicoService;

import java.util.List;

/**
 * HU-12 · Antecedentes patologicos: enfermedades previas, cirugias y
 * hospitalizaciones del paciente.
 *
 * ── Quien puede que ────────────────────────────────────────────────────────
 * LEER: ADMIN, MEDICO y ENFERMERA. La enfermera los consulta pero no los
 * edita ni los elimina (criterio 3): interpretar la historia clinica para
 * diagnosticar es del medico.
 *
 * REGISTRAR, EDITAR, ELIMINAR: solo MEDICO.
 *
 * El PACIENTE no entra: es la misma regla de todo endpoint clinico.
 */
@RestController
@RequestMapping("/antecedentes-patologicos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MEDICO','ENFERMERA')")
@Tag(name = "Antecedentes patologicos", description = "HU-12: enfermedades previas, cirugias y hospitalizaciones")
public class AntecedentePatologicoController {

    private final AntecedentePatologicoService antecedenteService;

    @Operation(
            summary = "Registrar un antecedente",
            description = "Lo firma el medico autenticado. 404 si el paciente no existe; "
                    + "400 si falta un campo, la fecha es futura o el tipo o el estado no son de la lista."
    )
    @PostMapping
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<AntecedentePatologicoResponseDto> crear(
            @Valid @RequestBody AntecedentePatologicoRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(antecedenteService.crear(request));
    }

    @Operation(
            summary = "Antecedentes de un paciente",
            description = "Del mas reciente al mas antiguo. Lista vacia si no tiene ninguno; 404 si el paciente no existe."
    )
    @GetMapping
    public ResponseEntity<List<AntecedentePatologicoResponseDto>> listar(@RequestParam Long pacienteId) {
        return ResponseEntity.ok(antecedenteService.listarDelPaciente(pacienteId));
    }

    @Operation(summary = "Editar un antecedente", description = "404 si no existe.")
    @PutMapping("/{antecedenteId}")
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<AntecedentePatologicoResponseDto> actualizar(
            @PathVariable Long antecedenteId,
            @Valid @RequestBody AntecedentePatologicoUpdateDto request) {
        return ResponseEntity.ok(antecedenteService.actualizar(antecedenteId, request));
    }

    @Operation(summary = "Eliminar un antecedente", description = "404 si no existe.")
    @DeleteMapping("/{antecedenteId}")
    @PreAuthorize("hasRole('MEDICO')")
    public ResponseEntity<Void> eliminar(@PathVariable Long antecedenteId) {
        antecedenteService.eliminar(antecedenteId);
        return ResponseEntity.noContent().build();
    }
}
