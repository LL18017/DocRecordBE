package ues.edu.sv.education.controller.userType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.userType.UserTypeRequestDto;
import ues.edu.sv.education.model.dto.userType.UserTypeResponseDto;
import ues.edu.sv.education.service.UserType.UserTypeService;

import java.util.List;

@RestController
@RequestMapping("/user-types")
@RequiredArgsConstructor
@SecurityRequirement(name = "jwt")
@Tag(
        name = "2. Tipos de Usuario",
        description = "Operaciones para la gestión de tipos de usuario"
)
public class UserTypeController {

    private final UserTypeService userTypeService;

    @Operation(
            summary = "Obtener todos los tipos de usuario",
            description = "Obtiene la lista de todos los tipos de usuario disponibles"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tipos de usuario obtenidos correctamente"
            )
    })
    @GetMapping
    public ResponseEntity<List<UserTypeResponseDto>> getAllUserTypes() {
        return ResponseEntity.ok(
                userTypeService.getAllUserType()
        );
    }

    @Operation(
            summary = "Obtener tipo de usuario por ID",
            description = "Obtiene un tipo de usuario mediante su identificador"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tipo de usuario encontrado"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Tipo de usuario no encontrado"
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<UserTypeResponseDto> getUserTypeById(
            @PathVariable Integer id
    ) {
        return ResponseEntity.ok(
                userTypeService.getUserTypeById(id)
        );
    }

    @Operation(
            summary = "Crear tipo de usuario",
            description = "Crea un nuevo tipo de usuario"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Tipo de usuario creado correctamente"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos inválidos"
            )
    })
    @PostMapping
    public ResponseEntity<UserTypeResponseDto> createUserType(
            @Valid @RequestBody UserTypeRequestDto dto
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(userTypeService.createUserType(dto));
    }

    @Operation(
            summary = "Editar tipo de usuario",
            description = "Actualiza un tipo de usuario existente"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tipo de usuario actualizado correctamente"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Tipo de usuario no encontrado"
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<UserTypeResponseDto> updateUserType(
            @PathVariable Integer id,
            @Valid @RequestBody UserTypeRequestDto dto
    ) {
        return ResponseEntity.ok(
                userTypeService.updateUserType(id, dto)
        );
    }

    @Operation(
            summary = "Eliminar tipo de usuario",
            description = "Elimina un tipo de usuario mediante su identificador"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Tipo de usuario eliminado correctamente"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Tipo de usuario no encontrado"
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUserType(
            @PathVariable Integer id
    ) {
        userTypeService.deleteUserType(id);

        return ResponseEntity.noContent().build();
    }
}