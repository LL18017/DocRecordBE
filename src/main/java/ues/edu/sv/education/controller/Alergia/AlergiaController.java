package ues.edu.sv.education.controller.Alergia;

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
import ues.edu.sv.education.model.dto.Alergia.AlergiaRequest;
import ues.edu.sv.education.model.dto.Alergia.AlergiaResponse;
import ues.edu.sv.education.model.dto.Alergia.AlergiaUpdateRequest;
import ues.edu.sv.education.service.Alergia.AlergiaService;

import java.util.Set;

@RestController
@RequestMapping("/api/alergias")
@RequiredArgsConstructor
@SecurityRequirement(name = "jwt")
@Tag(
        name = "9. Alergias",
        description = "Endpoints para gestionar las alergias de los usuarios"
)
public class AlergiaController {

    private final AlergiaService alergiaService;

    @Operation(
            summary = "Crear una alergia",
            description = "Registra una nueva alergia asociada a un usuario"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Alergia creada correctamente",
                    content = @Content(
                            schema = @Schema(
                                    implementation = AlergiaResponse.class
                            )
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
    public ResponseEntity<AlergiaResponse> crear(
            @Valid @RequestBody AlergiaRequest request
    ) {

        AlergiaResponse response = alergiaService.crear(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @Operation(
            summary = "Obtener una alergia",
            description = "Obtiene una alergia mediante su identificador"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Alergia encontrada"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Alergia no encontrada"
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<AlergiaResponse> obtenerPorId(

            @Parameter(
                    description = "ID de la alergia",
                    example = "1"
            )
            @PathVariable Integer id
    ) {

        return ResponseEntity.ok(
                alergiaService.obtenerPorId(id)
        );
    }

    @Operation(
            summary = "Obtener alergias de un usuario",
            description = "Obtiene todas las alergias asociadas a un usuario"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Alergias obtenidas correctamente"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Usuario no encontrado"
            )
    })
    @GetMapping("/usuario/{userId}")
    public ResponseEntity<Set<AlergiaResponse>> obtenerPorUsuario(

            @Parameter(
                    description = "ID del usuario",
                    example = "1"
            )
            @PathVariable Integer userId
    ) {

        return ResponseEntity.ok(
                alergiaService.obtenerPorUsuario(userId)
        );
    }

    @Operation(
            summary = "Actualizar una alergia",
            description = "Actualiza los datos de una alergia existente. "
                    + "Los campos vacíos o nulos conservan su valor actual"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Alergia actualizada correctamente"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos de entrada inválidos"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Alergia no encontrada"
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<AlergiaResponse> actualizar(

            @Parameter(
                    description = "ID de la alergia",
                    example = "1"
            )
            @PathVariable Integer id,

            @Valid @RequestBody AlergiaUpdateRequest request
    ) {

        return ResponseEntity.ok(
                alergiaService.actualizar(id, request)
        );
    }

    @Operation(
            summary = "Eliminar una alergia",
            description = "Elimina una alergia mediante su identificador"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Alergia eliminada correctamente"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Alergia no encontrada"
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(

            @Parameter(
                    description = "ID de la alergia",
                    example = "1"
            )
            @PathVariable Integer id
    ) {

        alergiaService.eliminar(id);

        return ResponseEntity.noContent().build();
    }
}