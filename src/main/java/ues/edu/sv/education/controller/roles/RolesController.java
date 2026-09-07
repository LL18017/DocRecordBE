package ues.edu.sv.education.controller.roles;

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
import ues.edu.sv.education.model.dto.roles.RoleRequestDto;
import ues.edu.sv.education.model.dto.roles.RoleResponseDto;
import ues.edu.sv.education.service.roles.RolesService;

import java.util.List;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@SecurityRequirement(name = "jwt")
@Tag(
        name = "1. Roles",
        description = "Operaciones para la gestión de roles"
)
public class RolesController {

    private final RolesService rolesService;

    @Operation(
            summary = "Obtener todos los roles",
            description = "Obtiene la lista de todos los roles disponibles"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Roles obtenidos correctamente")
    })
    @GetMapping
    public ResponseEntity<List<RoleResponseDto>> getAllRoles() {
        return ResponseEntity.ok(rolesService.getAllRoles());
    }

    @Operation(
            summary = "Obtener rol por ID",
            description = "Obtiene un rol específico mediante su identificador"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol encontrado"),
            @ApiResponse(responseCode = "404", description = "Rol no encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<RoleResponseDto> getRoleById(
            @PathVariable Integer id
    ) {
        return ResponseEntity.ok(rolesService.getRoleById(id));
    }

    @Operation(
            summary = "Crear un rol",
            description = "Crea un nuevo rol"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Rol creado correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos")
    })
    @PostMapping
    public ResponseEntity<RoleResponseDto> createRole(
            @Valid @RequestBody RoleRequestDto dto
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(rolesService.createRole(dto));
    }

    @Operation(
            summary = "Editar un rol",
            description = "Actualiza los datos de un rol existente"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol actualizado correctamente"),
            @ApiResponse(responseCode = "404", description = "Rol no encontrado")
    })
    @PutMapping("/{id}")
    public ResponseEntity<RoleResponseDto> updateRole(
            @PathVariable Integer id,
            @Valid @RequestBody RoleRequestDto dto
    ) {
        return ResponseEntity.ok(
                rolesService.updateRole(id, dto)
        );
    }

    @Operation(
            summary = "Eliminar un rol",
            description = "Elimina un rol mediante su identificador"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Rol eliminado correctamente"),
            @ApiResponse(responseCode = "404", description = "Rol no encontrado")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRole(
            @PathVariable Integer id
    ) {
        rolesService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }
}