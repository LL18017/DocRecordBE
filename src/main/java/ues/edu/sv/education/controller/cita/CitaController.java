package ues.edu.sv.education.controller.cita;

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
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.cita.CitaRequestDto;
import ues.edu.sv.education.model.dto.cita.CitaResponseDto;
import ues.edu.sv.education.model.dto.cita.CitaUpdateRequestDto;
import ues.edu.sv.education.service.Cita.CitaService;

import java.util.List;

@RestController
@RequestMapping("/citas")
@RequiredArgsConstructor
@SecurityRequirement(name = "jwt")
@Tag(
        name = "7. Citas",
        description = "Operaciones para la gestión de citas médicas"
)
public class CitaController {

    private final CitaService citaService;

    @Operation(
            summary = "Crear una cita",
            description = "Registra una nueva cita médica. " +
                    "Las citas deben programarse en intervalos exactos de 30 minutos."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Cita creada correctamente",
                    content = @Content(
                            schema = @Schema(implementation = CitaResponseDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos inválidos o horario no permitido"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autorizado"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Paciente, médico o tipo de cita no encontrado"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "El médico o paciente ya tiene una cita en ese horario"
            )
    })
    @PostMapping
    public ResponseEntity<CitaResponseDto> crear(
            @Valid @RequestBody CitaRequestDto dto
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(citaService.crear(dto));
    }

    @Operation(
            summary = "Obtener una cita",
            description = "Obtiene una cita médica mediante su identificador."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Cita encontrada",
                    content = @Content(
                            schema = @Schema(implementation = CitaResponseDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autorizado"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cita no encontrada"
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<CitaResponseDto> obtenerPorId(
            @Parameter(
                    description = "Identificador de la cita",
                    example = "1",
                    required = true
            )
            @PathVariable Integer id
    ) {
        return ResponseEntity.ok(
                citaService.obtenerPorId(id)
        );
    }

    @Operation(
            summary = "Obtener citas de un paciente",
            description = "Obtiene todas las citas asociadas a un paciente, " +
                    "ordenadas por fecha y hora."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Citas obtenidas correctamente"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autorizado"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Paciente no encontrado"
            )
    })
    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<CitaResponseDto>> obtenerPorPaciente(
            @Parameter(
                    description = "Identificador del paciente",
                    example = "10",
                    required = true
            )
            @PathVariable Integer pacienteId
    ) {
        return ResponseEntity.ok(
                citaService.obtenerPorPaciente(pacienteId)
        );
    }

    @Operation(
            summary = "Obtener citas de un médico",
            description = "Obtiene todas las citas asignadas a un médico, " +
                    "ordenadas por fecha y hora."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Citas obtenidas correctamente"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autorizado"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Médico no encontrado"
            )
    })
    @GetMapping("/medico/{medicoId}")
    public ResponseEntity<List<CitaResponseDto>> obtenerPorMedico(
            @Parameter(
                    description = "Identificador del médico",
                    example = "5",
                    required = true
            )
            @PathVariable Integer medicoId
    ) {
        return ResponseEntity.ok(
                citaService.obtenerPorMedico(medicoId)
        );
    }

    @Operation(
            summary = "Actualizar una cita",
            description = "Actualiza los datos de una cita existente. " +
                    "El nuevo horario debe cumplir con los intervalos de 30 minutos " +
                    "y estar disponible para el médico y paciente."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Cita actualizada correctamente",
                    content = @Content(
                            schema = @Schema(implementation = CitaResponseDto.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos inválidos o horario no permitido"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autorizado"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cita, paciente, médico o tipo de cita no encontrado"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "El horario seleccionado no está disponible"
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<CitaResponseDto> actualizar(
            @Parameter(
                    description = "Identificador de la cita",
                    example = "15",
                    required = true
            )
            @PathVariable Integer id,

            @Valid @RequestBody CitaUpdateRequestDto dto
    ) {
        return ResponseEntity.ok(
                citaService.actualizar(id, dto)
        );
    }

    @Operation(
            summary = "Eliminar una cita",
            description = "Elimina una cita médica existente."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Cita eliminada correctamente"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autorizado"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Cita no encontrada"
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @Parameter(
                    description = "Identificador de la cita",
                    example = "15",
                    required = true
            )
            @PathVariable Integer id
    ) {
        citaService.eliminar(id);

        return ResponseEntity.noContent().build();
    }
}