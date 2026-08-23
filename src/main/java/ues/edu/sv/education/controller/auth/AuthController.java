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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import ues.edu.sv.education.model.dto.auth.LoginResponseDto;
import ues.edu.sv.education.model.dto.auth.RegistroMedicoRequestDto;
import ues.edu.sv.education.model.dto.auth.RegistroMedicoResponseDto;
import ues.edu.sv.education.model.dto.auth.UserLoginDto;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
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

    /**
     * Donde vive el frontend. En desarrollo es localhost:3000; en el despliegue
     * sera otro dominio, de ahi la variable de entorno.
     */
    @Value("${app.frontend.url:http://localhost:3000}")
    private String urlDelFrontend;

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
            @ApiResponse(responseCode = "201", description = "Cuenta creada exitosamente. Revisar "
                    + "correoDeVerificacionEnviado: si viene en false la cuenta existe pero el correo "
                    + "de confirmacion no se pudo enviar, y hay que decirselo al usuario en vez de "
                    + "mandarlo a revisar una bandeja donde no va a llegar nada"),
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

    /**
     * Confirma una cuenta desde el enlace enviado por correo.
     *
     * A diferencia del resto de la API, este endpoint lo abre UNA PERSONA en su
     * navegador, no un cliente HTTP. Por eso nunca devuelve cuerpo: siempre
     * redirige al frontend con el resultado en la URL, tanto si sale bien como
     * si sale mal. Devolver texto plano dejaba al usuario mirando una pagina en
     * blanco servida por la API, con el puerto del backend a la vista.
     *
     * Por la misma razon las excepciones se capturan aqui en vez de dejarlas
     * subir al GlobalExceptionHandler: un JSON de error no le sirve a alguien
     * que viene de su bandeja de entrada.
     */
    @Operation(summary = "Confirmar registro por correo",
            description = "Redirige al frontend con el resultado; no devuelve cuerpo.")
    @GetMapping("/confirm")
    public ResponseEntity<Void> confirmAccount(@RequestParam("token") String token) {
        String estado;
        try {
            authService.confirmToken(token);
            estado = "ok";
        } catch (IllegalStateException e) {
            // El token ya se habia usado. NO es un error de cara al usuario:
            // pasa cuando alguien hace clic dos veces, o cuando el cliente de
            // correo pre-visita el enlace. La cuenta ya esta activa.
            estado = "ya-confirmada";
        } catch (GeneralException e) {
            estado = "expirado";
        } catch (NoResourceFoundException e) {
            estado = "invalido";
        }

        URI destino = UriComponentsBuilder.fromUriString(urlDelFrontend)
                .path("/confirmar")
                .queryParam("estado", estado)
                .build()
                .toUri();

        return ResponseEntity.status(HttpStatus.FOUND).location(destino).build();
    }
}