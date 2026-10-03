package ues.edu.sv.education.controller.CondicionHereditaria;

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
import ues.edu.sv.education.model.dto.CondicionHereditaria.CondicionHereditariaRequest;
import ues.edu.sv.education.model.dto.CondicionHereditaria.CondicionHereditariaResponse;
import ues.edu.sv.education.model.dto.CondicionHereditaria.CondicionHereditariaUpdateRequest;
import ues.edu.sv.education.service.CondicionHereditaria.CondicionHereditariaService;

import java.util.Set;

@RestController
@RequestMapping("/api/condiciones-hereditarias")
@SecurityRequirement(name = "jwt")
@RequiredArgsConstructor
@Tag(
        name = "10. Condiciones hereditarias",
        description = "Endpoints para gestionar las condiciones hereditarias de los usuarios"
)
public class CondicionHereditariaController {

    private final CondicionHereditariaService condicionHereditariaService;

    @Operation(
            summary = "Crear condición hereditaria",
            description = "Registra una nueva condición hereditaria asociada a un usuario"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Condición hereditaria creada correctamente",
                    content = @Content(
                            schema = @Schema(
                                    implementation = CondicionHereditariaResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos inválidos"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado"
            )
    })
    @PostMapping
    public ResponseEntity<CondicionHereditariaResponse> crear(
            @Valid @RequestBody CondicionHereditariaRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(condicionHereditariaService.crear(request));
    }

    @Operation(
            summary = "Obtener condición hereditaria",
            description = "Obtiene una condición hereditaria por su ID"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Condición hereditaria encontrada",
                    content = @Content(
                            schema = @Schema(
                                    implementation = CondicionHereditariaResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Condición hereditaria no encontrada"
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<CondicionHereditariaResponse> obtenerPorId(
            @Parameter(
                    description = "ID de la condición hereditaria",
                    example = "1"
            )
            @PathVariable Integer id
    ) {
        return ResponseEntity.ok(
                condicionHereditariaService.obtenerPorId(id)
        );
    }

    @Operation(
            summary = "Obtener condiciones hereditarias por usuario",
            description = "Obtiene todas las condiciones hereditarias asociadas a un usuario"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lista de condiciones hereditarias obtenida correctamente"
            )
    })
    @GetMapping("/usuario/{userId}")
    public ResponseEntity<Set<CondicionHereditariaResponse>> obtenerPorUsuario(
            @Parameter(
                    description = "ID del usuario",
                    example = "1"
            )
            @PathVariable Integer userId
    ) {
        return ResponseEntity.ok(
                condicionHereditariaService.obtenerPorUsuario(userId)
        );
    }

    @Operation(
            summary = "Actualizar condición hereditaria",
            description = "Actualiza parcialmente una condición hereditaria existente"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Condición hereditaria actualizada correctamente",
                    content = @Content(
                            schema = @Schema(
                                    implementation = CondicionHereditariaResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Condición hereditaria no encontrada"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos inválidos"
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<CondicionHereditariaResponse> actualizar(
            @Parameter(
                    description = "ID de la condición hereditaria",
                    example = "1"
            )
            @PathVariable Integer id,

            @Valid @RequestBody CondicionHereditariaUpdateRequest request
    ) {
        return ResponseEntity.ok(
                condicionHereditariaService.actualizar(id, request)
        );
    }

    @Operation(
            summary = "Eliminar condición hereditaria",
            description = "Elimina una condición hereditaria por su ID"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Condición hereditaria eliminada correctamente"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Condición hereditaria no encontrada"
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @Parameter(
                    description = "ID de la condición hereditaria",
                    example = "1"
            )
            @PathVariable Integer id
    ) {
        condicionHereditariaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}