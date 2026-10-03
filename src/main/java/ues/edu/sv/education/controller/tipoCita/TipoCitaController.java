package ues.edu.sv.education.controller.tipoCita;

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
import ues.edu.sv.education.model.dto.tipoCita.TipoCitaRequestDto;
import ues.edu.sv.education.model.dto.tipoCita.TipoCitaResponseDto;
import ues.edu.sv.education.service.TipoCita.TipoCitaService;

import java.util.List;

@RestController
@RequestMapping("/tipos-cita")
@RequiredArgsConstructor
@SecurityRequirement(name = "jwt")
@Tag(
        name = "6. Tipos de Cita",
        description = "Operaciones para la gestión de los tipos de citas médicas"
)
public class TipoCitaController {

    private final TipoCitaService tipoCitaService;

    @Operation(
            summary = "Crear un tipo de cita",
            description = "Registra un nuevo tipo de cita médica."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Tipo de cita creado correctamente",
                    content = @Content(
                            schema = @Schema(
                                    implementation = TipoCitaResponseDto.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos inválidos"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autorizado"
            )
    })
    @PostMapping
    public ResponseEntity<TipoCitaResponseDto> crear(
            @Valid @RequestBody TipoCitaRequestDto dto
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(tipoCitaService.crear(dto));
    }

    @Operation(
            summary = "Obtener todos los tipos de cita",
            description = "Obtiene la lista completa de tipos de citas médicas."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tipos de cita obtenidos correctamente"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autorizado"
            )
    })
    @GetMapping
    public ResponseEntity<List<TipoCitaResponseDto>> obtenerTodos() {
        return ResponseEntity.ok(
                tipoCitaService.obtenerTodos()
        );
    }

    @Operation(
            summary = "Obtener un tipo de cita",
            description = "Obtiene un tipo de cita mediante su identificador."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tipo de cita encontrado",
                    content = @Content(
                            schema = @Schema(
                                    implementation = TipoCitaResponseDto.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autorizado"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Tipo de cita no encontrado"
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<TipoCitaResponseDto> obtenerPorId(
            @Parameter(
                    description = "Identificador del tipo de cita",
                    example = "1",
                    required = true
            )
            @PathVariable Integer id
    ) {
        return ResponseEntity.ok(
                tipoCitaService.obtenerPorId(id)
        );
    }

    @Operation(
            summary = "Actualizar un tipo de cita",
            description = "Actualiza los datos de un tipo de cita existente."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tipo de cita actualizado correctamente",
                    content = @Content(
                            schema = @Schema(
                                    implementation = TipoCitaResponseDto.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos inválidos"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autorizado"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Tipo de cita no encontrado"
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<TipoCitaResponseDto> actualizar(
            @Parameter(
                    description = "Identificador del tipo de cita",
                    example = "1",
                    required = true
            )
            @PathVariable Integer id,

            @Valid @RequestBody TipoCitaRequestDto dto
    ) {
        return ResponseEntity.ok(
                tipoCitaService.actualizar(id, dto)
        );
    }

    @Operation(
            summary = "Eliminar un tipo de cita",
            description = "Elimina un tipo de cita existente."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Tipo de cita eliminado correctamente"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autorizado"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Tipo de cita no encontrado"
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @Parameter(
                    description = "Identificador del tipo de cita",
                    example = "1",
                    required = true
            )
            @PathVariable Integer id
    ) {
        tipoCitaService.eliminar(id);

        return ResponseEntity.noContent().build();
    }
}