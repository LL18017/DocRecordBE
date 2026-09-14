package ues.edu.sv.education.controller.auth;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.User.AltaUsuarioResponseDto;
import ues.edu.sv.education.model.dto.User.AsignarContrasenaRequestDto;
import ues.edu.sv.education.model.dto.User.UserRequestDto;
import ues.edu.sv.education.model.dto.User.CambiarEstadoUsuarioRequestDto;
import ues.edu.sv.education.model.dto.User.UserResponseDto;
import ues.edu.sv.education.model.dto.clinicas.ClinicasResponseDto;
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
    @Operation(
            summary = "Añadir un rol a un usuario",
            description = "Solo añade; no reemplaza los que ya tiene. Asignar ENFERMERA crea "
                    + "además su ficha en enfermería, porque sin esa fila la cuenta pasa el "
                    + "control de rol y luego recibe 403 al registrar constantes."
    )
    @PostMapping("/{userId}/role/{roleId}")
    public ResponseEntity<UserResponseDto> addRole(
            @PathVariable(required = true) int userId,
            @PathVariable(required = true) int roleId
    ) {
        return ResponseEntity.ok(userService.addRole(userId,roleId));
    }

    @Operation(
            summary = "Quitarle un rol a un usuario",
            description = "404 si no lo tenía. No deja quitarse uno mismo el rol de administrador "
                    + "ni retirar el último administrador del sistema: de eso no hay vuelta atrás "
                    + "desde la aplicación. Quitar ENFERMERA marca su ficha inactiva, no la borra "
                    + "—sus tomas de signos vitales la referencian—."
    )
    @DeleteMapping("/{userId}/role/{roleId}")
    public ResponseEntity<UserResponseDto> quitarRole(
            @PathVariable(required = true) int userId,
            @PathVariable(required = true) int roleId
    ) {
        return ResponseEntity.ok(userService.quitarRole(userId, roleId));
    }

    /*
     * Asignacion de sede, junto a la de rol porque es la misma decision: que
     * puede hacer esta cuenta y donde.
     *
     * Hace falta porque `clinicas.user_id` significa QUIEN REGISTRO la sede, no
     * quien trabaja en ella. Una enfermera nunca da de alta una clinica, asi
     * que sin esto su lista salia vacia y la pantalla de seleccion de clinica
     * la dejaba encallada en la puerta. Reasignar el dueño no servia: se la
     * quitaria al medico que la registro. Ver V10.
     */
    @Operation(
            summary = "Asignar una clínica a un usuario",
            description = "Le da acceso a operar en esa sede sin volverlo su dueño. "
                    + "404 si el usuario o la clínica no existen; 409 si ya la tenía asignada."
    )
    @PostMapping("/{userId}/clinica/{clinicaId}")
    public ResponseEntity<UserResponseDto> asignarClinica(
            @PathVariable(required = true) int userId,
            @PathVariable(required = true) int clinicaId
    ) {
        return ResponseEntity.ok(userService.asignarClinica(userId, clinicaId));
    }

    @Operation(
            summary = "Clínicas asignadas a un usuario",
            description = "Solo las asignadas como personal, no las que registró él mismo. "
                    + "Lo pide la pantalla de permisos para marcar cuáles ya tiene."
    )
    @GetMapping("/{userId}/clinicas")
    public ResponseEntity<List<ClinicasResponseDto>> clinicasAsignadas(
            @PathVariable(required = true) int userId
    ) {
        return ResponseEntity.ok(userService.clinicasAsignadas(userId));
    }

    @Operation(
            summary = "Quitarle una clínica a un usuario",
            description = "Retira la asignación; no borra la clínica ni afecta a su dueño. "
                    + "404 si el usuario o la clínica no existen, o si no la tenía asignada."
    )
    @DeleteMapping("/{userId}/clinica/{clinicaId}")
    public ResponseEntity<UserResponseDto> quitarClinica(
            @PathVariable(required = true) int userId,
            @PathVariable(required = true) int clinicaId
    ) {
        return ResponseEntity.ok(userService.quitarClinica(userId, clinicaId));
    }
    // @Valid, y no solo @RequestBody: sin el, las anotaciones de
    // UserRequestDto (@Email, @NotBlank, @Size) no se evaluaban y un cuerpo
    // invalido llegaba hasta el guardado de la Persona. El 400 salia entonces
    // de las restricciones de la ENTIDAD, asi que nombraba campos que el
    // cliente nunca envio -{"nombres":...,"apellidos":...}- y el frontend no
    // tenia como marcar el campo culpable. Con @Valid el fallo se detiene en
    // el borde y el cuerpo del error habla de email, userName o password.
    @PostMapping()
    public ResponseEntity<AltaUsuarioResponseDto> createRole(
            @Valid @RequestBody  UserRequestDto userRequestDto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(userRequestDto));
    }

    // Asignacion directa de contrasena por un administrador. Ademas del
    // hasRole('ADMIN') de clase, UserService.asignarContrasena vuelve a
    // resolver quien opera desde el token via AdminAutenticado -misma
    // defensa en profundidad que ConsultaService/MedicoAutenticado- y ahi
    // vive tambien la regla de que un admin no puede tocar la contrasena de
    // otro admin.
    // Activar o desactivar una cuenta (HU-05 criterio 3). Desactivar impide
    // iniciar sesion pero no borra nada: las consultas que firmo un medico
    // siguen firmadas por el aunque su cuenta quede cerrada, porque
    // ocurrieron. Las guardas contra dejar el sistema sin administracion
    // viven en UserService.cambiarEstado.
    @PatchMapping("/{userId}/estado")
    public ResponseEntity<UserResponseDto> cambiarEstado(
            @PathVariable("userId") int userId,
            @Valid @RequestBody CambiarEstadoUsuarioRequestDto request
    ) {
        return ResponseEntity.ok(userService.cambiarEstado(userId, request.activo()));
    }

    @PostMapping("/{userId}/password")
    public ResponseEntity<UserResponseDto> asignarContrasena(
            @PathVariable(required = true) int userId,
            @Valid @RequestBody AsignarContrasenaRequestDto request
    ) {
        return ResponseEntity.ok(userService.asignarContrasena(userId, request.password()));
    }
}
