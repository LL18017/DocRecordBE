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
            description = "Devuelve las clínicas del usuario autenticado"
    )
    @GetMapping("/mias")
    @PreAuthorize("hasAnyRole('ADMIN','MEDICO')")
    public ResponseEntity<List<ClinicasResponseDto>> obtenerMias() {
        return ResponseEntity.ok(
                clinicasService.obtenerPorUsuarioActual()
        );
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
            @PathVariable Integer clinicaId,
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
            @PathVariable Integer clinicaId
    ) {

        log.info("Eliminando clínica {}", clinicaId);

        clinicasService.eliminar(clinicaId);

        return ResponseEntity.noContent().build();
    }
}