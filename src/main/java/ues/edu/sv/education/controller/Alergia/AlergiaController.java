package ues.edu.sv.education.controller.Alergia;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.Alergia.AlergiaRequest;
import ues.edu.sv.education.model.dto.Alergia.AlergiaResponse;
import ues.edu.sv.education.service.Alergia.AlergiaService;

import java.util.List;

/**
 * HU-11 · Alergias del paciente: sustancia, reaccion, severidad y fecha de
 * deteccion, con quien las registro y quien las dio de baja.
 *
 * ── Quien puede que ────────────────────────────────────────────────────────
 * LEER: ADMIN, MEDICO y ENFERMERA.
 *
 * REGISTRAR y ELIMINAR: MEDICO y ENFERMERA. A diferencia de HU-12 y HU-13, la
 * enfermera si registra: muchas veces es quien primero oye "soy alergico a..."
 * al tomar las constantes, y esa informacion no puede esperar al medico.
 *
 * El PACIENTE no entra: es la misma regla de todo endpoint clinico.
 *
 * No hay edicion. Una alergia mal registrada se elimina y se vuelve a
 * registrar, y asi el rastro dice quien cambio que y cuando (criterio 4); una
 * edicion en sitio borraria lo que decia antes.
 *
 * Antes vivia bajo /api/alergias. El prefijo sobraba: Caddy ya quita /api
 * antes de llegar aqui, asi que el navegador habria tenido que pedir
 * /api/api/alergias.
 */
@RestController
@RequestMapping("/alergias")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MEDICO','ENFERMERA')")
@Tag(name = "Alergias", description = "HU-11: alergias del paciente")
public class AlergiaController {

    private final AlergiaService alergiaService;

    @Operation(
            summary = "Registrar una alergia",
            description = "La firma el médico o la enfermera autenticados. 404 si el paciente no existe; "
                    + "400 si falta un campo, la fecha es futura o la severidad no es LEVE, MODERADA o SEVERA; "
                    + "409 si el paciente ya tiene registrada esa sustancia (sin distinguir mayúsculas ni tildes)."
    )
    @PostMapping
    @PreAuthorize("hasAnyRole('MEDICO','ENFERMERA')")
    public ResponseEntity<AlergiaResponse> crear(@Valid @RequestBody AlergiaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alergiaService.crear(request));
    }

    @Operation(
            summary = "Alergias de un paciente",
            description = "Solo las vigentes, las SEVERAS primero. Lista vacía si no tiene ninguna; "
                    + "404 si el paciente no existe."
    )
    @GetMapping
    public ResponseEntity<List<AlergiaResponse>> listar(@RequestParam Long pacienteId) {
        return ResponseEntity.ok(alergiaService.listarDelPaciente(pacienteId));
    }

    @Operation(
            summary = "Ver una alergia",
            description = "Vigente o eliminada; si lo está, dice quién la eliminó y cuándo. 404 si no existe."
    )
    @GetMapping("/{id}")
    public ResponseEntity<AlergiaResponse> obtener(@PathVariable Integer id) {
        return ResponseEntity.ok(alergiaService.obtenerPorId(id));
    }

    @Operation(
            summary = "Eliminar una alergia",
            description = "Baja lógica: deja de listarse pero se conserva con quién la eliminó y cuándo. "
                    + "404 si no existe o ya estaba eliminada."
    )
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MEDICO','ENFERMERA')")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        alergiaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
