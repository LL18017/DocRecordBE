package ues.edu.sv.education.service.auth;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.model.dto.auth.RecuperacionResponseDto;
import ues.edu.sv.education.model.entity.TokenDeRestablecimiento;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.repository.TokenDeRestablecimientoRepository;
import ues.edu.sv.education.repository.UserRepository;
import ues.edu.sv.education.service.EmailService;
import ues.edu.sv.education.service.PlantillaDeCorreo;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * HU-04: recuperar la contrasena por correo, en dos pasos.
 *
 *   1. solicitar(correo)                  -> emite un token de 1 hora y manda el enlace
 *   2. restablecer(token, contrasenaNueva) -> cambia la contrasena y quema el enlace
 *
 * ── Lo que decide la forma de esta clase: el criterio 4 ───────────────────
 * "Dado un correo NO registrado, cuando solicito la recuperacion, entonces el
 * sistema responde igual que en el caso exitoso, para no revelar que correos
 * existen."
 *
 * Igualar el codigo HTTP y el cuerpo es la parte facil, y sola no alcanza. Si
 * para un correo registrado la peticion espera a que se guarde el token y a
 * que el servidor SMTP acepte el mensaje, y para uno inexistente responde de
 * inmediato, el reloj dice lo que el cuerpo calla. No es una diferencia
 * sutil: el envio de correo tiene 5 segundos de timeout configurados
 * (application.properties), asi que basta un cronometro para sacar la lista de
 * correos del personal probando direcciones.
 *
 * Por eso `solicitar` no consulta la base: encola la direccion recibida y
 * responde. Buscar la cuenta, emitir el token y mandar el correo ocurren
 * despues, en un hilo aparte, y nada de eso llega a la respuesta. La
 * consecuencia -- aceptada a proposito -- es que el cliente NO puede saber si
 * el correo se envio; pedirle esa informacion al servidor es justo lo que el
 * criterio 4 prohibe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecuperacionDeContrasenaService {

    private final UserRepository userRepository;
    private final TokenDeRestablecimientoRepository tokenRepository;
    private final EmailService emailService;
    // El mismo Argon2PasswordEncoder que /auth/register y POST /user/{id}/password
    // (Security/BasicConfiguration.passwordEncoder).
    private final PasswordEncoder passwordEncoder;

    /** Vigencia del enlace. HU-04 criterio 1 la fija en una hora. */
    private static final long VIGENCIA_EN_HORAS = 1;

    /**
     * La pantalla del frontend que pide la contrasena nueva.
     *
     * Este enlace apunta al FRONTEND, no al backend como el de /auth/confirm.
     * La diferencia es que confirmar no pide nada al usuario -- basta abrir la
     * direccion -- mientras que restablecer necesita un formulario donde
     * escribir la contrasena. Esa pantalla despues llama a
     * POST /auth/password/reset con el token.
     */
    private static final String RUTA_DEL_FORMULARIO = "/restablecer";

    /**
     * La respuesta de solicitar, palabra por palabra, exista o no la cuenta.
     *
     * Es una constante y no dos mensajes parecidos a proposito: en cuanto haya
     * dos textos, alguien los va a diferenciar "para ser mas util" y ahi se
     * pierde el criterio 4. El texto esta redactado para ser cierto en los dos
     * casos, sin prometer que llego nada.
     */
    private static final String MENSAJE_DE_SOLICITUD =
            "Si el correo está registrado, te enviamos un enlace para restablecer la contraseña. "
                    + "Revisa tu bandeja de entrada y la carpeta de spam; el enlace vence en 1 hora.";

    /**
     * Un unico motivo de rechazo para las tres formas de fallar: el token no
     * existe, ya se uso o ya vencio.
     *
     * Distinguirlas seria mas amable y diria de mas: "ya fue usado" confirma
     * que ese token existio y que pertenece a una cuenta real, que es
     * informacion util para quien esta probando tokens.
     */
    private static final String MENSAJE_DE_ENLACE_INVALIDO =
            "El enlace no es válido o ya venció. Solicita uno nuevo desde la pantalla de inicio de sesión.";

    private static final String MENSAJE_DE_EXITO =
            "Tu contraseña se actualizó. Ya puedes iniciar sesión con la contraseña nueva.";

    /** Donde vive el frontend; en el despliegue es otro dominio. */
    @Value("${app.frontend.url:http://localhost:3000}")
    private String urlDelFrontend;

    /**
     * El hilo donde se resuelve la solicitud, lejos del de la peticion.
     *
     * ── Por que aqui dentro y no como @Bean en una clase de configuracion ──
     * Un bean de tipo Executor en el contexto hace que Spring Boot retire su
     * propio `applicationTaskExecutor` (esta anotado @ConditionalOnMissingBean
     * (Executor.class)), es decir, cambiaria el ejecutor por defecto de toda
     * la aplicacion como efecto secundario de una decision que solo afecta a
     * este flujo. Declararlo aqui deja el resto del sistema intacto y pone el
     * mecanismo al lado del comentario que explica por que existe.
     *
     * ── Por que no @Async ─────────────────────────────────────────────────
     * @Async exige @EnableAsync -- otra vez, un cambio global -- y solo
     * funciona cuando la llamada atraviesa el proxy de Spring: una llamada
     * interna del mismo bean se ejecuta sincronamente y sin avisar. Ahi se
     * perderia en silencio justo la garantia por la que existe todo esto.
     *
     * ── Por que la cola es acotada y por que NO CallerRunsPolicy ──────────
     * 500 huecos: un envio tarda como mucho los 5 s del timeout de SMTP, asi
     * que la cola llena son minutos de trabajo pendiente, no horas, y una cola
     * ilimitada seria memoria que cualquiera puede hacer crecer desde un
     * endpoint publico. Al llenarse se rechaza (politica por defecto) y quien
     * encola lo registra. La politica habitual para no perder trabajo,
     * CallerRunsPolicy, esta descartada: ejecuta la tarea EN EL HILO DE LA
     * PETICION, que es exactamente la filtracion de tiempo que se quiere
     * evitar.
     *
     * Hilos demonio: recuperar una contrasena es una accion rara y manual; no
     * hay volumen que absorber, y lo que importa es que no sea el hilo de la
     * peticion, no cuantos sean.
     */
    private final ThreadPoolExecutor ejecutor = new ThreadPoolExecutor(
            1, 2, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500),
            tarea -> {
                Thread hilo = new Thread(tarea, "recuperacion-contrasena");
                hilo.setDaemon(true);
                return hilo;
            });

    /**
     * Al apagar se espera a lo que este en marcha: un correo a medio enviar
     * deja a alguien esperando un enlace que nunca llega, y el token ya quedo
     * guardado en la base.
     */
    @PreDestroy
    void detenerElEjecutor() {
        ejecutor.shutdown();
        try {
            if (!ejecutor.awaitTermination(20, TimeUnit.SECONDS)) {
                ejecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ejecutor.shutdownNow();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Paso 1: pedir el enlace
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Encola la solicitud y responde lo mismo siempre.
     *
     * Fijarse en lo que este metodo NO hace: no busca la cuenta, no consulta
     * la base, no toca el correo mas alla de normalizarlo. Todo lo que
     * dependeria de si la direccion existe ocurre en `emitirEnlace`, ya fuera
     * del hilo de la peticion. Es la unica forma de que el tiempo de respuesta
     * no delate la cuenta (HU-04 criterio 4).
     *
     * La normalizacion se hace aqui porque es un trabajo fijo -- trim y
     * minusculas sobre una cadena -- que cuesta lo mismo con cualquier
     * entrada, y asi al hilo de fondo le llega el correo ya en la forma en que
     * User.normalizarCorreo lo guarda.
     */
    public RecuperacionResponseDto solicitar(String correoRecibido) {
        String correo = correoRecibido == null ? "" : correoRecibido.trim().toLowerCase();

        try {
            ejecutor.execute(() -> emitirEnlace(correo));
        } catch (RejectedExecutionException e) {
            // La cola llena no depende del correo recibido, asi que rechazar
            // aqui no filtra nada; pero si significa que alguien se quedo sin
            // su enlace, y eso tiene que verse en el log.
            log.error("No se pudo encolar la solicitud de recuperacion de contrasena; la cola de "
                    + "envio esta llena. La persona no va a recibir el enlace.", e);
        }

        return new RecuperacionResponseDto(MENSAJE_DE_SOLICITUD);
    }

    /**
     * Lo que pasa de verdad, ya fuera del hilo de la peticion.
     *
     * Atrapa cualquier RuntimeException: aqui no hay nadie a quien
     * devolverle un error -- la respuesta HTTP ya se envio -- y una excepcion
     * que escapa de una tarea de un ThreadPoolExecutor se pierde sin dejar
     * mas rastro que la muerte del hilo.
     */
    private void emitirEnlace(String correo) {
        try {
            Optional<User> cuenta = userRepository.buscarConPersonaPorCorreo(correo);

            if (cuenta.isEmpty()) {
                // Se registra, y no por curiosidad: una racha de estas lineas
                // con direcciones distintas es como se ve un intento de
                // averiguar que correos existen.
                log.info("Solicitud de recuperacion para un correo no registrado ({}). "
                        + "No se emite token ni se envia nada.", correo);
                return;
            }

            User usuario = cuenta.get();

            String token = UUID.randomUUID().toString();
            tokenRepository.save(new TokenDeRestablecimiento(
                    null,
                    token,
                    usuario,
                    LocalDateTime.now().plusHours(VIGENCIA_EN_HORAS),
                    false));

            enviarElEnlace(usuario.getEmail(), usuario.getPersona().getNombres(), token);

        } catch (RuntimeException e) {
            log.error("Fallo la solicitud de recuperacion de contrasena para {}. "
                    + "Puede volver a pedirla.", correo, e);
        }
    }

    /**
     * Arma el enlace y manda el correo.
     *
     * La direccion se construye sobre app.frontend.url y NO con
     * ServletUriComponentsBuilder.fromCurrentContextPath(), como hacen los
     * correos de confirmacion, por dos razones independientes: este hilo no
     * tiene ninguna peticion HTTP asociada de la que sacar el contexto, y el
     * destino es una pantalla del frontend, no un endpoint de la API.
     *
     * Un fallo de SMTP se registra y se sigue: el token ya esta guardado y la
     * persona puede volver a pedir el enlace. Se captura MailException -- la
     * jerarquia de Spring -- y nada mas, igual que en AuthService: un catch
     * mas ancho se tragaria los fallos de base de datos.
     */
    private void enviarElEnlace(String destinatario, String nombre, String token) {
        String enlace = UriComponentsBuilder.fromUriString(urlDelFrontend)
                .path(RUTA_DEL_FORMULARIO)
                .queryParam("token", token)
                .build()
                .toUriString();

        // HashMap y no Map.of porque el nombre podria venir nulo y Map.of no
        // admite valores nulos; la plantilla sabe saludar sin nombre.
        Map<String, Object> datos = new HashMap<>();
        datos.put("nombre", nombre);
        datos.put("enlace", enlace);
        datos.put("horas", VIGENCIA_EN_HORAS);

        try {
            emailService.enviarCorreo(destinatario, PlantillaDeCorreo.RECUPERACION_DE_CONTRASENA, datos);
        } catch (MailException e) {
            log.error("No se pudo enviar el enlace de recuperacion a {}. El token quedo emitido y "
                    + "vigente por {} hora(s); la persona puede volver a solicitarlo.",
                    destinatario, VIGENCIA_EN_HORAS, e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Paso 2: canjear el enlace
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Cambia la contrasena si el enlace sirve, y lo quema.
     *
     * A quien se le cambia la contrasena lo dice el TOKEN, no el cuerpo de la
     * peticion: el correo ni siquiera se recibe aqui. Es la misma regla que en
     * el resto del sistema -- la identidad sale de la credencial.
     *
     * ── NO habilita la cuenta, y hubo que pensarlo ────────────────────────
     * La tentacion es poner enabled = true, como hace
     * UserService.asignarContrasena, con un argumento que suena bien: abrir
     * este enlace demuestra lo mismo que confirmar el registro -- que quien lo
     * abrio tiene acceso a esa bandeja de entrada -- asi que alguien cuyo
     * correo de confirmacion nunca llego podria desatascarse por aqui.
     *
     * No se hace, porque `users.enabled` YA NO significa una sola cosa. Nacio
     * como "el correo esta confirmado" (false en el alta, true en
     * /auth/confirm), pero HU-05 lo reutiliza como el interruptor de
     * activa/desactivada que maneja un administrador
     * (UserService.cambiarEstado). Con un unico booleano para las dos ideas,
     * habilitar aqui significaria que cualquiera a quien acaban de desactivar
     * se reactiva solo: pide recuperar su contrasena, abre el enlace de SU
     * bandeja de entrada y vuelve a entrar. Eso no es recuperar una
     * contrasena, es saltarse una decision administrativa.
     *
     * Se acepta el precio: quien nunca recibio su correo de confirmacion puede
     * cambiar la contrasena aqui y aun asi recibir "Usuario no ha confirmado
     * su cuenta aun" al entrar. No queda atrapado -- volver a registrarse con
     * el mismo correo regenera el enlace de confirmacion mientras la cuenta
     * siga sin confirmar (ver AuthService.registrarMedico) -- pero es un
     * rodeo. La solucion de verdad es separar las dos ideas en dos columnas,
     * y eso le toca a HU-05, no a esta historia.
     *
     * ── No se cierran las sesiones abiertas ───────────────────────────────
     * JwtService no tiene revocacion: los access token ya emitidos valen hasta
     * su "exp". Es la misma limitacion conocida que documenta
     * UserService.asignarContrasena, y la mitigacion es que duran 15 minutos.
     * Vale la pena tenerla presente aqui porque es peor: si la contrasena se
     * esta recuperando porque la cuenta estaba comprometida, el intruso
     * conserva su sesion ese rato.
     */
    @Transactional
    public RecuperacionResponseDto restablecer(String token, String contrasenaNueva) {

        TokenDeRestablecimiento enlace = tokenRepository.findByToken(token)
                .orElseThrow(() -> new GeneralException(MENSAJE_DE_ENLACE_INVALIDO, "400"));

        // Usado o vencido dan el MISMO error que inexistente (criterio 3).
        if (!enlace.sirve()) {
            throw new GeneralException(MENSAJE_DE_ENLACE_INVALIDO, "400");
        }

        // Solo la contrasena. `enabled` se deja como esta: ver la nota de
        // arriba sobre por que tocarlo aqui seria saltarse una desactivacion.
        User usuario = enlace.getUsuario();
        usuario.setPassword(passwordEncoder.encode(contrasenaNueva));
        userRepository.save(usuario);

        // Se queman TODOS los enlaces pendientes de esta cuenta, no solo el
        // que se acaba de canjear. Si habia varios -- porque la persona pulso
        // el boton tres veces, o porque alguien mas pidio recuperar esta
        // cuenta -- esos correos siguen en alguna bandeja con enlaces vivos, y
        // despues de un restablecimiento no representan mas que riesgo.
        List<TokenDeRestablecimiento> pendientes =
                tokenRepository.findByUsuarioAndUsadoFalse(usuario);
        pendientes.forEach(pendiente -> pendiente.setUsado(true));
        tokenRepository.saveAll(pendientes);

        log.info("Contrasena restablecida por enlace de correo para el usuario {}. "
                + "Se invalidaron {} enlace(s) pendiente(s).", usuario.getUserID(), pendientes.size());

        return new RecuperacionResponseDto(MENSAJE_DE_EXITO);
    }
}
