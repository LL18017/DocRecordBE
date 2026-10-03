package ues.edu.sv.education.controller.enfermedadCronica;

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
import ues.edu.sv.education.model.dto.EnfermedadCronica.EnfermedadCronicaRequest;
import ues.edu.sv.education.model.dto.EnfermedadCronica.EnfermedadCronicaResponse;
import ues.edu.sv.education.model.dto.EnfermedadCronica.EnfermedadCronicaUpdateRequest;
import ues.edu.sv.education.service.enfermedadCronica.EnfermedadCronicaService;

import java.util.Set;

@RestController
@RequestMapping("/api/enfermedades-cronicas")
@RequiredArgsConstructor
@SecurityRequirement(name = "jwt")
@Tag(
        name = "8. Enfermedades crónicas",
        description = "Endpoints para gestionar las enfermedades crónicas de los usuarios"
)
public class EnfermedadCronicaController {

    private final EnfermedadCronicaService enfermedadCronicaService;

    @Operation(
            summary = "Crear una enfermedad crónica",
            description = "Registra una nueva enfermedad crónica asociada a un usuario"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Enfermedad crónica creada correctamente",
                    content = @Content(
                            schema = @Schema(implementation = EnfermedadCronicaResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos de entrada inválidos"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado"
            )
    })
    @PostMapping
    public ResponseEntity<EnfermedadCronicaResponse> crear(
            @Valid @RequestBody EnfermedadCronicaRequest request
    ) {

        EnfermedadCronicaResponse response =
                enfermedadCronicaService.crear(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @Operation(
            summary = "Obtener una enfermedad crónica",
            description = "Obtiene una enfermedad crónica mediante su identificador"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Enfermedad crónica encontrada"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Enfermedad crónica no encontrada"
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<EnfermedadCronicaResponse> obtenerPorId(
            @Parameter(
                    description = "ID de la enfermedad crónica",
                    example = "1"
            )
            @PathVariable Integer id
    ) {

        return ResponseEntity.ok(
                enfermedadCronicaService.obtenerPorId(id)
        );
    }

    @Operation(
            summary = "Obtener enfermedades crónicas de un usuario",
            description = "Obtiene todas las enfermedades crónicas asociadas a un usuario"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Enfermedades crónicas obtenidas correctamente"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado"
            )
    })
    @GetMapping("/usuario/{userId}")
    public ResponseEntity<Set<EnfermedadCronicaResponse>> obtenerPorUsuario(
            @Parameter(
                    description = "ID del usuario",
                    example = "1"
            )
            @PathVariable Integer userId
    ) {

        return ResponseEntity.ok(
                enfermedadCronicaService.obtenerPorUsuario(userId)
        );
    }

    @Operation(
            summary = "Actualizar una enfermedad crónica",
            description = "Actualiza los datos de una enfermedad crónica existente"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Enfermedad crónica actualizada correctamente"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos de entrada inválidos"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Enfermedad crónica no encontrada"
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<EnfermedadCronicaResponse> actualizar(
            @Parameter(
                    description = "ID de la enfermedad crónica",
                    example = "1"
            )
            @PathVariable Integer id,

            @Valid @RequestBody EnfermedadCronicaUpdateRequest request
    ) {

        return ResponseEntity.ok(
                enfermedadCronicaService.actualizar(id, request)
        );
    }

    @Operation(
            summary = "Eliminar una enfermedad crónica",
            description = "Elimina una enfermedad crónica mediante su identificador"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Enfermedad crónica eliminada correctamente"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Enfermedad crónica no encontrada"
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @Parameter(
                    description = "ID de la enfermedad crónica",
                    example = "1"
            )
            @PathVariable Integer id
    ) {

        enfermedadCronicaService.eliminar(id);

        return ResponseEntity.noContent().build();
    }
}