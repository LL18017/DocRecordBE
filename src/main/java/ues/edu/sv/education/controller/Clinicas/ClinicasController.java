package ues.edu.sv.education.controller.Clinicas;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.clinicas.ClinicasRequestDto;
import ues.edu.sv.education.model.dto.clinicas.ClinicasResponseDto;
import ues.edu.sv.education.model.dto.clinicas.ClinicasUpdateRequestDto;
import ues.edu.sv.education.service.Clinicas.ClinicaService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/clinics")
@RequiredArgsConstructor
@SecurityRequirement(name = "jwt")
@Tag(
        name = "5. Clínicas",
        description = "Endpoints para gestionar las clínicas de los usuarios"
)
public class ClinicasController {

    private final ClinicaService clinicasService;

    @Operation(
            summary = "Obtener clínicas de un usuario",
            description = "Devuelve todas las clínicas asociadas al usuario indicado"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Clínicas obtenidas correctamente"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autorizado"
            )
    })
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ClinicasResponseDto>> obtenerPorUsuario(
            @Parameter(
                    description = "ID del usuario",
                    required = true
            )
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
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Clínica creada correctamente"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos de entrada inválidos"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autorizado"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "El usuario no tiene permisos para crear clínicas"
            )
    })
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
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Clínica actualizada correctamente"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos de entrada inválidos"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autorizado"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Clínica no encontrada"
            )
    })
    @PutMapping("/{clinicaId}")
    public ResponseEntity<ClinicasResponseDto> editar(
            @Parameter(
                    description = "ID de la clínica",
                    required = true
            )
            @PathVariable Integer clinicaId,

            @Valid @RequestBody ClinicasUpdateRequestDto dto
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
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Clínica eliminada correctamente"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autorizado"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Clínica no encontrada"
            )
    })
    @DeleteMapping("/{clinicaId}")
    public ResponseEntity<Void> eliminar(
            @Parameter(
                    description = "ID de la clínica",
                    required = true
            )
            @PathVariable Integer clinicaId,

            @Parameter(
                    description = "ID del usuario que solicita la eliminación",
                    required = true
            )
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