package ues.edu.sv.education.controller.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.User.UserRequestDto;
import ues.edu.sv.education.model.dto.User.UserResponseDto;
import ues.edu.sv.education.service.user.UserService;

import java.util.List;

// Administracion de cuentas: alta directa, listado y asignacion de roles.
// Todo el controller es hasRole('ADMIN') porque cada endpoint expone algo
// que un usuario cualquiera no deberia poder hacer ni ver: crear cuentas,
// asignarse roles (incluido ADMIN, sin este guard en un solo paso), o leer
// el correo de todo el personal.
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserControler {
    private final UserService userService;

    // Aqui vivia un segundo listado, `@GetMapping` sin ruta, que devolvia
    // List<User>: la ENTIDAD JPA cruda. Se elimino, no se convirtio a DTO,
    // porque /user/all -- justo abajo -- ya hace lo mismo bien y nadie lo
    // llamaba: el frontend documenta en services/usuarios.ts que lo evita a
    // proposito, y en este repositorio getAllUser() no tenia otro uso.
    // Mantener dos listados equivalentes solo dejaba abierto el peligroso.
    //
    // Devolvia la entidad tal cual, y eso tenia dos consecuencias medidas
    // contra la API en marcha:
    //   1. serializaba el campo `password`, o sea el hash argon2 de TODOS los
    //      usuarios, en un endpoint que responde con la lista completa;
    //   2. entraba en el ciclo user -> roles -> users -> roles..., asi que
    //      respondia 200 con un JSON truncado de ~53 KB, invalido, con el
    //      error del servidor pegado al final. Inservible, ademas de inseguro.
    //
    // La regla que deja: por este controller salen DTOs, nunca entidades. Un
    // DTO decide que se publica; una entidad publica todo lo que tenga la
    // tabla hoy y todo lo que alguien le agregue manana.
    @GetMapping("/all")
    public ResponseEntity<List<UserResponseDto>> getAll(
            @RequestParam(defaultValue = "0") int inicio,
            @RequestParam(defaultValue = "20") int fin
    ) {
        return ResponseEntity.ok(userService.getAll(inicio,fin));
    }
    @PostMapping("/{userId}/role/{roleId}")
    public ResponseEntity<UserResponseDto> addRole(
            @PathVariable(required = true) int userId,
            @PathVariable(required = true) int roleId
    ) {
        return ResponseEntity.ok(userService.addRole(userId,roleId));
    }
    // @Valid, y no solo @RequestBody: sin el, las anotaciones de
    // UserRequestDto (@Email, @NotBlank, @Size) no se evaluaban y un cuerpo
    // invalido llegaba hasta el guardado de la Persona. El 400 salia entonces
    // de las restricciones de la ENTIDAD, asi que nombraba campos que el
    // cliente nunca envio -{"nombres":...,"apellidos":...}- y el frontend no
    // tenia como marcar el campo culpable. Con @Valid el fallo se detiene en
    // el borde y el cuerpo del error habla de email, userName o password.
    @PostMapping()
    public ResponseEntity<UserResponseDto> createRole(
            @Valid @RequestBody  UserRequestDto userRequestDto
    ) {
        return ResponseEntity.ok(userService.createUser(userRequestDto));
    }
}
