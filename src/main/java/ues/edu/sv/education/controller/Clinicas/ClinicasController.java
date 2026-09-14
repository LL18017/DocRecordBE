package ues.edu.sv.education.controller.Clinicas;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.clinicas.CambiarEstadoClinicaRequestDto;
import ues.edu.sv.education.model.dto.clinicas.ClinicasRequestDto;
import ues.edu.sv.education.model.dto.clinicas.ClinicasResponseDto;
import ues.edu.sv.education.service.Clinicas.ClinicaService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/clinics")
@RequiredArgsConstructor
@Tag(
        name = "Clínicas",
        description = "Endpoints para gestionar las clínicas de los usuarios"
)
public class ClinicasController {

    private final ClinicaService clinicasService;

    @Operation(
            summary = "Obtener mis clínicas",
            description = "Las clínicas en las que el usuario autenticado puede operar: las que "
                    + "registró y aquellas a las que se le asignó como personal."
    )
    // ENFERMERA incluida, a diferencia del resto de este controlador. Leer en
    // qué sedes trabaja uno no es administrar sedes: sin este permiso la
    // pantalla de selección de clínica respondía 403 y dejaba a enfermería
    // encallada en la puerta, sin poder llegar a lo único que sí le toca, que
    // es tomar constantes.
    @GetMapping("/mias")
    @PreAuthorize("hasAnyRole('ADMIN','MEDICO','ENFERMERA')")
    public ResponseEntity<List<ClinicasResponseDto>> obtenerMias() {
        return ResponseEntity.ok(
                clinicasService.obtenerPorUsuarioActual()
        );
    }

    @Operation(
            summary = "Listar todas las clínicas",
            description = "El catálogo completo, sin filtrar por dueño. Lo necesita quien asigna "
                    + "personal a una sede: para asignar hay que poder ver las sedes ajenas, que "
                    + "es justo lo que /mias no devuelve."
    )
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ClinicasResponseDto>> listarTodas() {
        return ResponseEntity.ok(clinicasService.listarTodas());
    }

    @Operation(
            summary = "Crear una clínica",
            description = "Crea una clínica para el usuario autenticado"
    )
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MEDICO')")
    public ResponseEntity<ClinicasResponseDto> crear(
            @Valid @RequestBody ClinicasRequestDto dto
    ) {

        ClinicasResponseDto response = clinicasService.crear(dto);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @Operation(
            summary = "Editar una clínica",
            description = "Modifica los datos de una clínica existente. Un MEDICO solo puede editar las suyas; un ADMIN puede editar cualquiera."
    )
    @PutMapping("/{clinicaId}")
    @PreAuthorize("hasAnyRole('ADMIN','MEDICO')")
    public ResponseEntity<ClinicasResponseDto> editar(
            @PathVariable("clinicaId") Integer clinicaId,
            @Valid @RequestBody ClinicasRequestDto dto
    ) {

        log.info("Editando clínica con ID: {}", clinicaId);

        ClinicasResponseDto response =
                clinicasService.editar(clinicaId, dto);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Eliminar una clínica",
            description = "Elimina una clínica existente. Un MEDICO solo puede eliminar las suyas; un ADMIN puede eliminar cualquiera."
    )
    @DeleteMapping("/{clinicaId}")
    @PreAuthorize("hasAnyRole('ADMIN','MEDICO')")
    public ResponseEntity<Void> eliminar(
            @PathVariable("clinicaId") Integer clinicaId
    ) {

        log.info("Eliminando clínica {}", clinicaId);

        clinicasService.eliminar(clinicaId);

        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Dar de alta o de baja una clínica",
            description = "Cambia el estado entre ACTIVA e INACTIVA. NO borra nada: las consultas "
                    + "que se atendieron ahí ocurrieron ahí, y el personal asignado sigue asignado. "
                    + "Una clínica inactiva solo deja de ofrecerse para atender."
    )
    @PatchMapping("/{clinicaId}/estado")
    @PreAuthorize("hasAnyRole('ADMIN','MEDICO')")
    public ResponseEntity<ClinicasResponseDto> cambiarEstado(
            @PathVariable("clinicaId") Integer clinicaId,
            @Valid @RequestBody CambiarEstadoClinicaRequestDto request
    ) {
        return ResponseEntity.ok(clinicasService.cambiarEstado(clinicaId, request.estado()));
    }
}
