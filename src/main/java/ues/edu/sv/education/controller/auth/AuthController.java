package ues.edu.sv.education.controller.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
import ues.edu.sv.education.model.dto.auth.RecuperacionRequestDto;
import ues.edu.sv.education.model.dto.auth.RecuperacionResponseDto;
import ues.edu.sv.education.model.dto.auth.RestablecerContrasenaRequestDto;
import ues.edu.sv.education.model.dto.auth.RegistroMedicoRequestDto;
import ues.edu.sv.education.model.dto.auth.RegistroMedicoResponseDto;
import ues.edu.sv.education.model.dto.auth.UserLoginDto;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.service.auth.AuthService;
import ues.edu.sv.education.service.auth.RecuperacionDeContrasenaService;
import ues.edu.sv.education.service.auth.UserAuthService;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Endpoints para login, refresh de token y registro de usuarios")
public class AuthController {

    private final UserAuthService service;
    private final AuthService authService;
    private final RecuperacionDeContrasenaService recuperacionService;

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
    public ResponseEntity<LoginResponseDto> getUser(@Valid @RequestBody UserLoginDto user,
                                                    HttpServletRequest peticion) {
        return ResponseEntity.ok(authService.loging(user, ipDeOrigen(peticion)));
    }

    /**
     * IP desde la que se hizo la peticion, para el aviso de inicio de sesion.
     *
     * Es getRemoteAddr() a secas y no una lectura manual de X-Forwarded-For, y
     * eso es deliberado. Detras del proxy inverso del despliegue, getRemoteAddr()
     * devolveria la IP del proxy; quien lo arregla es
     * server.forward-headers-strategy=native (application.properties), que hace
     * que Tomcat sustituya ese valor por el del cliente PERO solo cuando la
     * conexion viene de un proxy de su lista de confianza.
     *
     * Leer la cabecera aqui a mano seria creersela siempre, incluso en una
     * peticion directa contra el puerto de la aplicacion: cualquiera podria
     * decidir que IP queda escrita en un aviso de seguridad. Delegarlo en la
     * valve es lo que mantiene el dato utilizable.
     *
     * Aun asi, esta IP vale lo que valga el proxy: solo es fiable si el proxy
     * SOBRESCRIBE o ANADE la cabecera en vez de reenviar la que mando el
     * cliente (ver la nota de application.properties). Se guarda como pista
     * para el usuario, no como prueba.
     *
     * Se toma en el controller y se pasa como argumento en vez de que el
     * servicio la busque en un RequestContextHolder: asi AuthService no depende
     * de que exista una peticion HTTP en el hilo, y en la firma se ve que el
     * dato viene de fuera.
     */
    private String ipDeOrigen(HttpServletRequest peticion) {
        return peticion.getRemoteAddr();
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
     * HU-04, paso 1: pedir el enlace para restablecer la contraseña.
     *
     * ── 202 y no 200, y siempre el mismo cuerpo ───────────────────────────
     * El criterio 4 de la historia exige que un correo registrado y uno que no
     * existe se respondan EXACTAMENTE igual. 202 Accepted es además lo que de
     * verdad ocurre: la solicitud se acepta y se resuelve después, fuera del
     * hilo de la petición, precisamente para que el tiempo de respuesta
     * tampoco delate si la cuenta existe (ver RecuperacionDeContrasenaService).
     *
     * Ese diseño tiene una consecuencia visible para el cliente y conviene que
     * esté escrita: esta respuesta NO significa que el correo se haya enviado,
     * y nunca va a significarlo. Un campo tipo correoDeVerificacionEnviado
     * -como el que sí devuelve /auth/register- sería justo la filtración que
     * la historia prohíbe.
     */
    @Operation(
            summary = "Solicitar el enlace de recuperación de contraseña",
            description = "Responde siempre lo mismo, exista o no la cuenta, y sin diferencia de "
                    + "tiempo observable: el cliente no puede averiguar por aquí qué correos están "
                    + "registrados. Si existe, se le envía un enlace que vence en 1 hora."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Solicitud aceptada. NO confirma que "
                    + "el correo exista ni que el mensaje se haya enviado"),
            @ApiResponse(responseCode = "400", description = "El correo viene vacío o con un formato inválido")
    })
    @PostMapping("/password/forgot")
    public ResponseEntity<RecuperacionResponseDto> solicitarRecuperacion(
            @Valid @RequestBody RecuperacionRequestDto request) {
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(recuperacionService.solicitar(request.email()));
    }

    /**
     * HU-04, paso 2: canjear el enlace por una contraseña nueva.
     *
     * A quién se le cambia la contraseña lo dice el token, no el cuerpo: aquí
     * no se recibe ningún correo ni ningún id de usuario. Es la misma regla
     * que en el resto del sistema —la identidad sale de la credencial, nunca
     * de lo que el cliente afirme ser—.
     *
     * Este endpoint SÍ devuelve cuerpo, a diferencia de /auth/confirm, porque
     * lo llama el formulario del frontend por fetch, no el navegador siguiendo
     * un enlace del correo.
     */
    @Operation(
            summary = "Restablecer la contraseña con el token del correo",
            description = "El token debe estar vigente y sin usar. Un token inexistente, ya usado "
                    + "o vencido se rechazan los tres con el mismo 400 y el mismo mensaje."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Contraseña actualizada; el enlace queda usado"),
            @ApiResponse(responseCode = "400", description = "El enlace no es válido, ya se usó o venció")
    })
    @PostMapping("/password/reset")
    public ResponseEntity<RecuperacionResponseDto> restablecerContrasena(
            @Valid @RequestBody RestablecerContrasenaRequestDto request) {
        return ResponseEntity.ok(
                recuperacionService.restablecer(request.token(), request.password()));
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