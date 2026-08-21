package ues.edu.sv.education.controller.Clinicas;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
            summary = "Obtener clínicas de un usuario",
            description = "Devuelve todas las clínicas asociadas al usuario indicado"
    )
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ClinicasResponseDto>> obtenerPorUsuario(
            @PathVariable Integer userId
    ) {

        log.info("Obteniendo clínicas del usuario: {}", userId);

        return ResponseEntity.ok(
                clinicasService.obtenerPorUsuario(userId)
        );
    }

    @Operation(
            summary = "Crear una clínica",
            description = "Crea una clínica asociada a un usuario autorizado"
    )
    @PostMapping
    public ResponseEntity<ClinicasResponseDto> crear(
            @Valid @RequestBody ClinicasRequestDto dto
    ) {

        log.info("Creando clínica para el usuario: {}", dto.userId());

        ClinicasResponseDto response = clinicasService.crear(dto);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @Operation(
            summary = "Editar una clínica",
            description = "Modifica los datos de una clínica existente"
    )
    @PutMapping("/{clinicaId}")
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
            description = "Elimina una clínica existente"
    )
    @DeleteMapping("/{clinicaId}")
    public ResponseEntity<Void> eliminar(
            @PathVariable Integer clinicaId,
            @RequestParam Integer userId
    ) {

        log.info(
                "Eliminando clínica {} solicitada por usuario {}",
                clinicaId,
                userId
        );

        clinicasService.eliminar(clinicaId, userId);

        return ResponseEntity.noContent().build();
    }
}