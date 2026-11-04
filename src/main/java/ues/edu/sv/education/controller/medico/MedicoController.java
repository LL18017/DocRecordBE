package ues.edu.sv.education.controller.medico;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.medico.AccesoRequestDto;
import ues.edu.sv.education.model.dto.medico.AccesoResponseDto;
import ues.edu.sv.education.model.dto.medico.EstadoDeMedicoRequestDto;
import ues.edu.sv.education.model.dto.medico.MedicoRequestDto;
import ues.edu.sv.education.model.dto.medico.MedicoResponseDto;
import ues.edu.sv.education.service.medico.MedicoService;

import java.util.List;

/**
 * Fichas de medico (HU-02): el registro del profesional y, aparte, su acceso.
 *
 * Solo ADMIN, todo: es la unica alta de medicos que queda desde que se retiro
 * el registro publico, y dar de alta a quien atiende pacientes es responder
 * por el. Las listas de medicos que necesitan otras pantallas (agenda,
 * horarios) tienen sus propios endpoints de lectura.
 */
@RestController
@RequestMapping("/medicos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Medicos", description = "Fichas de medico y su acceso al sistema")
public class MedicoController {

    private final MedicoService medicoService;

    @Operation(summary = "Listar las fichas de medico",
            description = "Por apellido. activo es opcional: true solo las vigentes, false solo las dadas de baja. "
                    + "Cada ficha trae su cuenta (null si aun no tiene acceso) y el estado de esa cuenta.")
    @GetMapping
    public ResponseEntity<List<MedicoResponseDto>> listar(@RequestParam(required = false) Boolean activo) {
        return ResponseEntity.ok(medicoService.listar(activo));
    }

    @Operation(summary = "Ver una ficha de medico", description = "404 si no existe.")
    @GetMapping("/{personaId}")
    public ResponseEntity<MedicoResponseDto> obtener(@PathVariable("personaId") Long personaId) {
        return ResponseEntity.ok(medicoService.obtener(personaId));
    }

    @Operation(
            summary = "Registrar la ficha de un medico",
            description = "Nombres, apellidos, especialidad y numero de junta (HU-02 c1); telefono opcional. "
                    + "No crea cuenta: el acceso se da despues con POST /medicos/{id}/acceso. Con userId la "
                    + "ficha se cuelga de una cuenta que ya existe (un administrador que tambien ejerce). "
                    + "400 si falta un dato o la junta no tiene formato; 404 si la especialidad o la cuenta "
                    + "no existen; 409 si la junta es de otro medico o la persona ya tiene ficha."
    )
    @PostMapping
    public ResponseEntity<MedicoResponseDto> crear(@Valid @RequestBody MedicoRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(medicoService.crear(request));
    }

    @Operation(summary = "Corregir una ficha de medico",
            description = "Mismos datos y reglas que el registro; userId se ignora. 404 si no existe.")
    @PutMapping("/{personaId}")
    public ResponseEntity<MedicoResponseDto> actualizar(
            @PathVariable("personaId") Long personaId,
            @Valid @RequestBody MedicoRequestDto request) {
        return ResponseEntity.ok(medicoService.actualizar(personaId, request));
    }

    @Operation(summary = "Dar de baja o reactivar una ficha",
            description = "La baja no borra nada: la ficha queda inactiva y la cuenta pierde el rol MEDICO. "
                    + "Reactivar no devuelve el rol: se vuelve a dar acceso.")
    @PatchMapping("/{personaId}/estado")
    public ResponseEntity<MedicoResponseDto> cambiarEstado(
            @PathVariable("personaId") Long personaId,
            @Valid @RequestBody EstadoDeMedicoRequestDto request) {
        return ResponseEntity.ok(medicoService.cambiarEstado(personaId, request.activo()));
    }

    @Operation(
            summary = "Dar acceso al sistema a un medico",
            description = "Sin cuenta: la crea con email, rol MEDICO y la sede clinicaId, y manda una invitacion "
                    + "para que elija su contrasena (la cuenta queda sin activar hasta canjearla en "
                    + "POST /auth/invitacion). Con cuenta: le da el rol MEDICO y la sede si faltan, y si la "
                    + "cuenta no se ha activado manda una invitacion nueva. 400 si falta el correo o la sede; "
                    + "409 si el correo ya es de otra cuenta o la ficha esta dada de baja; 404 si no existe."
    )
    @PostMapping("/{personaId}/acceso")
    public ResponseEntity<AccesoResponseDto> darAcceso(
            @PathVariable("personaId") Long personaId,
            @Valid @RequestBody AccesoRequestDto request) {
        return ResponseEntity.ok(medicoService.darAcceso(personaId, request));
    }
}
