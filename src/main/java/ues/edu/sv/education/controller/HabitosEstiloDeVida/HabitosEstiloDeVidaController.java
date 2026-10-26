
package ues.edu.sv.education.controller.HabitosEstiloDeVida;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import ues.edu.sv.education.model.dto.habitosestilodevida.HabitoHistorialResponse;
import ues.edu.sv.education.model.dto.habitosestilodevida.HabitosEstiloDeVidaRequest;
import ues.edu.sv.education.model.dto.habitosestilodevida.HabitosEstiloDeVidaResponse;
import ues.edu.sv.education.model.dto.tipohabito.TipoHabitoRequest;
import ues.edu.sv.education.model.dto.tipohabito.TipoHabitoResponse;
import ues.edu.sv.education.service.habitosestilodevida.HabitosEstiloDeVidaService;
import ues.edu.sv.education.service.tipohabito.TipoHabitoService;

import java.util.List;

@RestController
@RequestMapping("/api/habitos-estilo-de-vida")
@RequiredArgsConstructor
@SecurityRequirement(name = "jwt")
@Tag(
        name = "10. Hábitos de estilo de vida",
        description = "Gestión de hábitos de estilo de vida y sus tipos"
)
@PreAuthorize("hasAnyRole('ADMIN','MEDICO','ENFERMERA')")
public class HabitosEstiloDeVidaController {

    private final HabitosEstiloDeVidaService habitosService;
    private final TipoHabitoService tipoHabitoService;

    // =========================
    // HÁBITOS DE ESTILO DE VIDA
    // =========================

    @Operation(
            summary = "Registrar un hábito",
            description = "Tipo, nivel, frecuencia (obligatoria) y observaciones (opcionales). Queda con la "
                    + "fecha de actualización y quién lo registró, que sale del token."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Hábito creado",
                    content = @Content(
                            schema = @Schema(
                                    implementation = HabitosEstiloDeVidaResponse.class
                            )
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Paciente o tipo no encontrado")
    })
    @PreAuthorize("hasAnyRole('MEDICO','ENFERMERA')")
    @PostMapping
    public ResponseEntity<HabitosEstiloDeVidaResponse> crearHabito(
            @Valid @RequestBody HabitosEstiloDeVidaRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(habitosService.crear(request));
    }

    @Operation(summary = "Listar hábitos")
    @GetMapping
    public ResponseEntity<List<HabitosEstiloDeVidaResponse>> listarHabitos() {
        return ResponseEntity.ok(habitosService.listar());
    }

    @Operation(summary = "Obtener un hábito por ID")
    @GetMapping("/{id}")
    public ResponseEntity<HabitosEstiloDeVidaResponse> obtenerHabito(
            @Parameter(description = "ID del hábito", example = "1")
            @PathVariable Long id) {

        return ResponseEntity.ok(habitosService.buscarPorId(id));
    }

    @Operation(summary = "Listar hábitos de un paciente")
    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<HabitosEstiloDeVidaResponse>> listarPorPaciente(
            @Parameter(description = "ID del paciente", example = "1")
            @PathVariable Long pacienteId) {

        return ResponseEntity.ok(
                habitosService.listarPorPaciente(pacienteId)
        );
    }

    @Operation(
            summary = "Historial de un hábito",
            description = "Las versiones anteriores del hábito (HU-14, criterio 2), la más reciente "
                    + "primero, con quién y cuándo las escribió y quién y cuándo las reemplazó. "
                    + "Lista vacía si nunca se editó; 404 si el hábito no existe."
    )
    @GetMapping("/{id}/historial")
    public ResponseEntity<List<HabitoHistorialResponse>> historialDeHabito(
            @Parameter(description = "ID del hábito", example = "1")
            @PathVariable Long id) {

        return ResponseEntity.ok(habitosService.historial(id));
    }

    @Operation(summary = "Actualizar un hábito")
    @PreAuthorize("hasAnyRole('MEDICO','ENFERMERA')")
    @PutMapping("/{id}")
    public ResponseEntity<HabitosEstiloDeVidaResponse> actualizarHabito(
            @PathVariable Long id,
            @Valid @RequestBody HabitosEstiloDeVidaRequest request) {

        return ResponseEntity.ok(
                habitosService.actualizar(id, request)
        );
    }

    @Operation(summary = "Eliminar un hábito")
    @PreAuthorize("hasRole('MEDICO')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarHabito(
            @PathVariable Long id) {

        habitosService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    // =========================
    // TIPOS DE HÁBITO
    // =========================

    @Operation(summary = "Crear un tipo de hábito")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/tipos")
    public ResponseEntity<TipoHabitoResponse> crearTipoHabito(
            @Valid @RequestBody TipoHabitoRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tipoHabitoService.crear(request));
    }

    @Operation(summary = "Listar tipos de hábito")
    @GetMapping("/tipos")
    public ResponseEntity<List<TipoHabitoResponse>> listarTiposHabito() {
        return ResponseEntity.ok(tipoHabitoService.listar());
    }

    @Operation(summary = "Obtener un tipo de hábito por ID")
    @GetMapping("/tipos/{id}")
    public ResponseEntity<TipoHabitoResponse> obtenerTipoHabito(
            @Parameter(description = "ID del tipo de hábito", example = "1")
            @PathVariable Integer id) {

        return ResponseEntity.ok(
                tipoHabitoService.buscarPorId(id)
        );
    }

    @Operation(summary = "Actualizar un tipo de hábito")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/tipos/{id}")
    public ResponseEntity<TipoHabitoResponse> actualizarTipoHabito(
            @PathVariable Integer id,
            @Valid @RequestBody TipoHabitoRequest request) {

        return ResponseEntity.ok(
                tipoHabitoService.actualizar(id, request)
        );
    }

    @Operation(summary = "Eliminar un tipo de hábito")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/tipos/{id}")
    public ResponseEntity<Void> eliminarTipoHabito(
            @PathVariable Integer id) {

        tipoHabitoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
