package ues.edu.sv.education.controller.Clinicas;

import io.swagger.v3.oas.annotations.Operation;
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
import ues.edu.sv.education.model.dto.cita.CitaResponseDto;
import ues.edu.sv.education.model.dto.horario.HorarioRequestDto;
import ues.edu.sv.education.model.dto.horario.HorarioResponseDto;
import ues.edu.sv.education.model.dto.horario.MedicoDeClinicaDto;
import ues.edu.sv.education.service.horario.HorarioDeMedicoService;

import java.util.List;

/**
 * HU-29 · Medicos de una sede con sus dias y horarios.
 *
 * Leer la ficha lo puede todo el personal; cambiar asignaciones y horarios es
 * solo del administrador (criterio 5: cualquier otro rol recibe 403).
 */
@RestController
@RequestMapping("/clinics/{clinicaId}/medicos")
@RequiredArgsConstructor
@SecurityRequirement(name = "jwt")
@Tag(name = "Médicos de una clínica", description = "Asignación de médicos a una sede con sus horarios (HU-29)")
public class MedicosDeClinicaController {

    private final HorarioDeMedicoService horarios;

    @Operation(summary = "Médicos de la sede", description = "Cada médico con su especialidad y sus horarios en la sede.")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MEDICO','ENFERMERA')")
    public ResponseEntity<List<MedicoDeClinicaDto>> listar(@PathVariable Integer clinicaId) {
        return ResponseEntity.ok(horarios.medicosDeLaClinica(clinicaId));
    }

    @Operation(
            summary = "Agregar un horario",
            description = "Asigna el médico a la sede si aún no lo estaba. Se rechaza con 409 si se traslapa " +
                    "con otro horario del mismo médico, en esta sede o en otra."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Horario agregado"),
            @ApiResponse(responseCode = "400", description = "Horas inválidas o el usuario no es médico"),
            @ApiResponse(responseCode = "403", description = "Solo el administrador"),
            @ApiResponse(responseCode = "409", description = "Se traslapa con otro horario del médico")
    })
    @PostMapping("/{medicoId}/horarios")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HorarioResponseDto> agregarHorario(
            @PathVariable Integer clinicaId,
            @PathVariable Integer medicoId,
            @Valid @RequestBody HorarioRequestDto dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(horarios.agregarHorario(clinicaId, medicoId, dto));
    }

    @Operation(summary = "Quitar un horario")
    @DeleteMapping("/{medicoId}/horarios/{horarioId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> quitarHorario(
            @PathVariable Integer clinicaId,
            @PathVariable Integer medicoId,
            @PathVariable Integer horarioId
    ) {
        horarios.quitarHorario(clinicaId, horarioId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Citas futuras del médico en la sede",
            description = "Lo que se muestra antes de confirmar que se le quita la asignación (criterio 4)."
    )
    @GetMapping("/{medicoId}/citas-futuras")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CitaResponseDto>> citasFuturas(
            @PathVariable Integer clinicaId,
            @PathVariable Integer medicoId
    ) {
        return ResponseEntity.ok(horarios.citasFuturas(clinicaId, medicoId));
    }

    @Operation(summary = "Quitar al médico de la sede", description = "Retira la asignación y sus horarios en la sede.")
    @DeleteMapping("/{medicoId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> quitarDeLaClinica(
            @PathVariable Integer clinicaId,
            @PathVariable Integer medicoId
    ) {
        horarios.quitarDeLaClinica(clinicaId, medicoId);
        return ResponseEntity.noContent().build();
    }
}
