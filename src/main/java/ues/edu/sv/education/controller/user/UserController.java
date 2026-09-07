package ues.edu.sv.education.controller.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.User.UserRequestDto;
import ues.edu.sv.education.model.dto.User.UserResponseDto;
import ues.edu.sv.education.service.user.UserService;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "jwt")
@Tag(
        name = "4. Usuarios",
        description = "Endpoints para consultar y administrar los usuarios del sistema"
)
public class UserController {

    private final UserService userService;

    /**
     * Obtiene una lista paginada de usuarios.
     */
    @Operation(
            summary = "Listar usuarios",
            description = "Obtiene una lista paginada de todos los usuarios registrados."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuarios obtenidos correctamente"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "No autorizado")
    })
    @GetMapping
    public ResponseEntity<List<UserResponseDto>> getAll(
            @Parameter(
                    description = "Número de página. Comienza en 0.",
                    example = "0",
                    in = ParameterIn.QUERY
            )
            @RequestParam(defaultValue = "0") Integer inicio,

            @Parameter(
                    description = "Cantidad de usuarios por página.",
                    example = "10",
                    in = ParameterIn.QUERY
            )
            @RequestParam(defaultValue = "10") Integer fin
    ) {
        return ResponseEntity.ok(
                userService.getAll(inicio, fin)
        );
    }

    /**
     * Obtiene un usuario por su ID.
     */
    @Operation(
            summary = "Obtener usuario por ID",
            description = "Obtiene la información de un usuario específico."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario encontrado"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponseDto> getById(
            @Parameter(
                    description = "Identificador del usuario",
                    example = "1",
                    required = true
            )
            @PathVariable Integer userId
    ) {
        return ResponseEntity.ok(
                userService.getById(userId)
        );
    }

    @GetMapping("/all")
    @Operation(
            summary = "Obteniene todos los usuarios",
            description = "Obtiene la información todos los usuarios"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuarios encontrados"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "No autorizado"),
    })
    public ResponseEntity<List<UserResponseDto>> findAll() {
        return ResponseEntity.ok(
                userService.findAll()
        );
    }
    /**
     * Actualiza los datos administrables de un usuario.
     *
     * La contraseña no se administra desde este endpoint.
     */
    @Operation(
            summary = "Actualizar usuario",
            description = "Actualiza los datos administrables de un usuario. "
                    + "La contraseña es gestionada por el servicio de autenticación."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario actualizado correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    @PutMapping("/{userId}")
    public ResponseEntity<UserResponseDto> updateUser(
            @Parameter(
                    description = "Identificador del usuario",
                    example = "1",
                    required = true
            )
            @PathVariable Integer userId,

            @Valid @RequestBody UserRequestDto userRequest
    ) {
        return ResponseEntity.ok(
                userService.updateUser(userId, userRequest)
        );
    }

    /**
     * Elimina un usuario.
     */
    @Operation(
            summary = "Eliminar usuario",
            description = "Elimina permanentemente un usuario del sistema."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Usuario eliminado correctamente"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(
            @Parameter(
                    description = "Identificador del usuario",
                    example = "1",
                    required = true
            )
            @PathVariable Integer userId
    ) {
        userService.deleteUser(userId);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }

    /**
     * Agrega un rol a un usuario.
     */
    @Operation(
            summary = "Agregar rol a usuario",
            description = "Asigna un rol existente a un usuario."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol agregado correctamente"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "No autorizado"),
            @ApiResponse(responseCode = "404", description = "Usuario o rol no encontrado"),
            @ApiResponse(responseCode = "409", description = "El usuario ya posee el rol")
    })
    @PostMapping("/{userId}/roles/{roleId}")
    public ResponseEntity<UserResponseDto> addRole(
            @Parameter(
                    description = "Identificador del usuario",
                    example = "1",
                    required = true
            )
            @PathVariable Integer userId,

            @Parameter(
                    description = "Identificador del rol",
                    example = "2",
                    required = true
            )
            @PathVariable Integer roleId
    ) {
        return ResponseEntity.ok(
                userService.addRole(userId, roleId)
        );
    }
}