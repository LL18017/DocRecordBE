package ues.edu.sv.education.controller.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.auth.LoginResponseDto;
import ues.edu.sv.education.model.dto.auth.RegistroMedicoRequestDto;
import ues.edu.sv.education.model.dto.auth.RegistroMedicoResponseDto;
import ues.edu.sv.education.model.dto.auth.UserLoginDto;
import ues.edu.sv.education.service.auth.AuthService;
import ues.edu.sv.education.service.auth.UserAuthService;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Endpoints para login, refresh de token y registro de usuarios")
public class AuthController {

    private final UserAuthService service;
    private final AuthService authService;

    @Operation(
            summary = "Iniciar sesión",
            description = "Autentica a un usuario con email y contraseña, y devuelve un access token junto con un refresh token."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login exitoso, retorna los tokens y los roles del usuario"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos (email o contraseña con formato incorrecto)"),
            @ApiResponse(responseCode = "401", description = "Credenciales incorrectas")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> getUser(@Valid @RequestBody UserLoginDto user) {
        return ResponseEntity.ok(authService.loging(user));
    }

    @Operation(
            summary = "Refrescar token",
            description = "Genera un nuevo access token a partir de un refresh token válido enviado en el header Authorization."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token renovado exitosamente"),
            @ApiResponse(responseCode = "401", description = "Refresh token inválido, expirado o ausente")
    })
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDto> refresh(
            @Parameter(description = "Refresh token en formato 'Bearer {token}'", required = true)
            @RequestHeader(HttpHeaders.AUTHORIZATION) String token) {
        return ResponseEntity.ok(authService.refresh(token));
    }

    @Operation(
            summary = "Registrar médico",
            description = "Autogestion publica: crea persona + usuario + medico con rol MEDICO. El rol no es configurable por el cliente."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Cuenta creada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos (nombres, apellidos, email, contraseña o especialidad faltantes/incorrectos)"),
            @ApiResponse(responseCode = "404", description = "La especialidad indicada no existe"),
            @ApiResponse(responseCode = "409", description = "El email ya está registrado y confirmado")
    })
    @PostMapping("/register")
    public ResponseEntity<RegistroMedicoResponseDto> registrarMedico(@Valid @RequestBody RegistroMedicoRequestDto request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(authService.registrarMedico(request));
    }

    @Operation(summary = "Confirmar registro por correo")
    @GetMapping("/confirm")
    public ResponseEntity<String> confirmAccount(@RequestParam String token) {
        authService.confirmToken(token);
        return ResponseEntity.ok("Cuenta confirmada exitosamente");
    }
}