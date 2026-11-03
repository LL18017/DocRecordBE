package ues.edu.sv.education.service.auth;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.auth.CustomUserDetails;
import ues.edu.sv.education.model.dto.auth.LoginResponseDto;
import ues.edu.sv.education.model.dto.auth.UserLoginDto;
import ues.edu.sv.education.model.entity.*;
import ues.edu.sv.education.model.enums.EventCodeEnums;
import ues.edu.sv.education.model.enums.EventStatusEnums;
import ues.edu.sv.education.model.enums.RolesEnum;
import ues.edu.sv.education.model.mappers.RoleMapper;
import ues.edu.sv.education.repository.*;
import ues.edu.sv.education.service.EmailService;
import ues.edu.sv.education.service.PlantillaDeCorreo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final EventTypeRepository eventTypeRepository;
    private final EventStatusRepository eventStatusRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;
    private final UserAuthService service;
    private final JwtService jwtService;
    private final AuthenticationManager authManager;
    private static final long TOKEN_EXPIRATION_HOURS = 24; // HU-02 criterio 3

    /**
     * Autentica y deja registrado el evento que disparara el aviso por correo.
     *
     * @param ipDeOrigen desde donde se conecto quien inicio sesion, ya resuelta
     *                   por el controller. Ver AuthController.ipDeOrigen: detras
     *                   de un proxy inverso solo es la IP del usuario si Tomcat
     *                   la reescribio (server.forward-headers-strategy=native),
     *                   y solo es fiable si el proxy sobrescribe la cabecera.
     *                   Es una pista para el usuario, no una prueba.
     */
    public LoginResponseDto loging(UserLoginDto loginDto, String ipDeOrigen) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginDto.email(),
                        loginDto.password()
                )
        );

        CustomUserDetails user =
                (CustomUserDetails) auth.getPrincipal();

        assert user != null;
        LoginResponseDto response = new LoginResponseDto(
                auth.getName(),
                jwtService.generateToken(user),
                jwtService.generateRefreshToken(user),
                auth.getAuthorities().stream().map(a -> RoleMapper.toDto(a.getAuthority())).toList()
        );


        Event loginEvent = Event.builder()
                .eventType(
                        eventTypeRepository.getReferenceById(EventCodeEnums.LOGIN.getId())
                )
                .description("Se ha detectado un nuevo inicio de sesión")
                .eventStatus(
                        eventStatusRepository.getReferenceById(EventStatusEnums.PENDING.getId())
                )
                // La columna existia desde V1 y nunca se llenaba, asi que el
                // aviso de seguridad no decia desde donde se entro -- que es lo
                // unico que permite reconocer un acceso ajeno. La plantilla ya
                // sabia pintarla y omitirla si venia vacia; solo faltaba el dato.
                .ipAddress(ipDeOrigen)
                .userEmail(user.getUsername())
                .createdAt(LocalDateTime.now())
                .build();

        eventRepository.save(loginEvent);
        return response;
    }

    public LoginResponseDto refresh(String authHeader) {
        System.out.println(authHeader);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Token invalido");
        }
        authHeader = authHeader.substring(7);
        String userEmail = jwtService.extractUserName(authHeader);
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("usuario invalido");
        }
        if (jwtService.isTokenExpired(authHeader)) {
            throw new IllegalArgumentException("fecha expirada");
        }
        User u = service.getUser(userEmail);
        // Un refresh token emitido antes de restablecer la contrasena ya no
        // sirve (HU-03 criterio 4); si no, bastaria con el para abrir una
        // sesion nueva y el cierre de sesiones no cerraria nada.
        if (jwtService.versionDeSesion(jwtService.getClaims(authHeader)) != u.getVersionDeSesion()) {
            throw new GeneralException("Tu sesión se cerró porque se restableció la contraseña. Inicia sesión de nuevo.", "401");
        }
        CustomUserDetails user = new CustomUserDetails(u);
        Persona persona = u.getPersona();
        return new LoginResponseDto(persona.getNombres() + " " + persona.getApellidos(), jwtService.generateToken(user), jwtService.generateRefreshToken(user),
                user.getAuthorities().stream().map(a -> RoleMapper.toDto(a.getAuthority())).toList()
        );
    }

    // Aqui vivia registrarMedico, el alta publica de una cuenta de medico por
    // /auth/register. Se retiro (HU-02, decision del PO): cualquiera podia
    // darse de alta como medico y, con la cuenta confirmada, leer todos los
    // expedientes. Ahora al medico lo da de alta el administrador: POST /user
    // crea la cuenta y manda la confirmacion, y POST /user/{id}/role/{MEDICO}
    // crea la ficha con especialidad y numero de junta (UserService.addRole).

    @Transactional
    public void confirmToken(String token) {
        VerificationToken verificationToken = verificationTokenRepository
                .findByToken(token)
                .orElseThrow(() -> new NoResourceFoundException("Token no existe","404"));

        if (verificationToken.isUsed()) {
            throw new IllegalStateException("El token ya fue utilizado");
        }

        if (verificationToken.isExpired()) {
            // Se avisa por correo ANTES de cortar. Quien llega aqui viene de su
            // bandeja de entrada, hizo clic y solo va a ver una pagina que dice
            // "expirado"; el correo es lo que le explica que hacer para salir del
            // atasco, y le queda guardado. El envio no puede tumbar la respuesta:
            // el enlace sigue vencido con correo o sin el.
            avisarQueElEnlaceVencio(verificationToken.getUser().getEmail());
            throw new GeneralException("El token ha expirado");
        }

        User user = verificationToken.getUser();
        user.setEnabled(true);
        userRepository.save(user);

        verificationToken.setUsed(true);
        verificationTokenRepository.save(verificationToken);
    }

    /**
     * Avisa que el enlace de confirmacion ya no sirve y como conseguir otro.
     *
     * El "como": desde que no hay registro publico, las cuentas las crea el
     * administrador, y es el quien manda un enlace nuevo (POST
     * /user/{id}/confirmacion, UserService.reenviarConfirmacion). El correo le
     * dice a la persona que se lo pida.
     *
     * Igual que en el registro, un fallo de correo se registra y se sigue: la
     * respuesta al usuario no depende de que el SMTP este de buenas.
     */
    private void avisarQueElEnlaceVencio(String toEmail) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("horas", TOKEN_EXPIRATION_HOURS);
        try {
            emailService.enviarCorreo(toEmail, PlantillaDeCorreo.TOKEN_EXPIRADO, datos);
        } catch (MailException e) {
            log.error("No se pudo avisar a {} de que su enlace de confirmacion vencio. "
                    + "El administrador puede reenviarle la confirmacion.", toEmail, e);
        }
    }
}
