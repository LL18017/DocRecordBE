package ues.edu.sv.education.service.auth;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.auth.CustomUserDetails;
import ues.edu.sv.education.model.dto.auth.LoginResponseDto;
import ues.edu.sv.education.model.dto.auth.RegistroMedicoRequestDto;
import ues.edu.sv.education.model.dto.auth.RegistroMedicoResponseDto;
import ues.edu.sv.education.model.dto.auth.UserLoginDto;
import ues.edu.sv.education.model.dto.especialidad.EspecialidadResponseDto;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PersonaRepository personaRepository;
    private final EspecialidadRepository especialidadRepository;
    private final MedicoRepository medicoRepository;
    private final RoleRepository roleRepository;
    private final EventRepository eventRepository;
    private final EventTypeRepository eventTypeRepository;
    private final EventStatusRepository eventStatusRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;
    private final UserAuthService service;
    private final JwtService jwtService;
    private final AuthenticationManager authManager;
    private final PasswordEncoder passwordEncoder;
    private static final long TOKEN_EXPIRATION_MINUTES = 5;

    /**
     * Donde vive el formulario de registro, que es el mecanismo de reenvio del
     * enlace de confirmacion: registrarse otra vez con el mismo correo
     * sobreescribe la cuenta no confirmada y genera un token nuevo. El correo de
     * "enlace vencido" manda ahi.
     */
    @Value("${app.frontend.url:http://localhost:3000}")
    private String urlDelFrontend;

    public LoginResponseDto loging(UserLoginDto loginDto) {
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
        CustomUserDetails user = new CustomUserDetails(u);
        Persona persona = u.getPersona();
        return new LoginResponseDto(persona.getNombres() + " " + persona.getApellidos(), jwtService.generateToken(user), jwtService.generateRefreshToken(user),
                user.getAuthorities().stream().map(a -> RoleMapper.toDto(a.getAuthority())).toList()
        );
    }

    // /auth/register: autogestion publica de una cuenta de medico. Crea
    // Persona + User + Medico en una sola transaccion; el rol MEDICO y la
    // especialidad los decide el servidor y la especialidad recibida,
    // nunca un rol elegido por el cliente (ver el commit de seguridad que
    // le quito `roles` a este flujo).
    @Transactional
    public RegistroMedicoResponseDto registrarMedico(RegistroMedicoRequestDto request) {

        Especialidad especialidad = especialidadRepository.findById(request.especialidadId())
                .orElseThrow(() -> new NoResourceFoundException("Especialidad no encontrada", "404"));

        Optional<User> userOptional = userRepository.findByEmailContainingIgnoreCase(request.email());

        User userToSave;
        Persona persona;

        if (userOptional.isPresent()) {
            User existingUser = userOptional.get();

            if (existingUser.isEnabled()) {
                throw new IllegalStateException("El correo ya está registrado y confirmado");
            }

            // Cuenta no confirmada: se sobreescribe con los datos nuevos
            userToSave = existingUser;
            persona = existingUser.getPersona();
            persona.setNombres(request.nombres());
            persona.setApellidos(request.apellidos());
            personaRepository.save(persona);
            userToSave.setPassword(passwordEncoder.encode(request.password()));

            // Invalida cualquier token anterior de esta cuenta
            verificationTokenRepository.deleteAllByUser(existingUser);

        } else {
            persona = personaRepository.save(
                    Persona.builder()
                            .nombres(request.nombres())
                            .apellidos(request.apellidos())
                            .build()
            );
            userToSave = User.builder()
                    .persona(persona)
                    .email(request.email())
                    .password(passwordEncoder.encode(request.password()))
                    .roles(new HashSet<>())
                    .build();
        }

        Role medico = roleRepository.getReferenceById(RolesEnum.MEDICO.getId());
        // HashSet, no Set.of(): al reintentar un registro no confirmado
        // userToSave es una entidad ya persistida, y Hibernate reemplaza la
        // coleccion de roles vaciandola primero. Set.of(...) es inmutable y
        // esa limpieza revienta con UnsupportedOperationException.
        userToSave.setRoles(new HashSet<>(Set.of(medico)));

        // El usuario queda deshabilitado hasta que confirme el correo
        userToSave.setEnabled(false);

        User savedUser = userRepository.save(userToSave);

        // Reintento de un registro no confirmado: reutiliza la fila de
        // medicos que ya existia para esa persona en vez de duplicarla.
        // OJO: no fijar personaId a mano en el builder de abajo. Con @MapsId
        // el id se deriva de `persona` al persistir; si el campo @Id ya trae
        // valor, Spring Data asume que la fila existe y hace merge() en vez
        // de persist(), y Hibernate revienta con "null identifier" porque
        // esta fila nunca se guardo antes.
        Medico medicoEntity = medicoRepository.findById(persona.getPersonaId())
                .orElseGet(() -> Medico.builder()
                        .persona(persona)
                        .activo(true)
                        .build());
        medicoEntity.setEspecialidad(especialidad);
        medicoRepository.save(medicoEntity);

        // Generar y guardar el token de verificación
        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken(
                null,
                token,
                savedUser,
                LocalDateTime.now().plusMinutes(TOKEN_EXPIRATION_MINUTES),
                false
        );
        verificationTokenRepository.save(verificationToken);

        // Enviar correo de confirmacion. Si falla NO se propaga: la cuenta ya
        // esta creada y el token guardado, y eso vale mas que la notificacion.
        boolean correoEnviado = enviarCorreoDeVerificacion(savedUser.getEmail(), persona.getNombres(), token);

        return new RegistroMedicoResponseDto(
                savedUser.getUserID(),
                savedUser.getEmail(),
                persona.getNombres(),
                persona.getApellidos(),
                List.of(medico.getName()),
                new EspecialidadResponseDto(
                        especialidad.getEspecialidadId(),
                        especialidad.getNombre(),
                        especialidad.isActiva()
                ),
                correoEnviado
        );
    }

    /**
     * Envia el correo de confirmacion y responde si salio o no.
     *
     * Crear la cuenta y avisar por correo NO valen lo mismo: la cuenta es el
     * dato, el correo es una notificacion. Antes el envio iba dentro de la
     * transaccion de alta y sin proteccion, asi que cualquier tropiezo de
     * Gmail (credenciales rotadas, cuota agotada, SMTP caido) subia hasta el
     * GlobalExceptionHandler y devolvia 500: nadie podia registrarse mientras
     * un servicio EXTERNO estuviera mal. Aqui el fallo se absorbe y la cuenta
     * sobrevive, deshabilitada y con su token valido, igual que siempre.
     *
     * Se captura MailException -la jerarquia de Spring para el envio, que
     * envuelve las jakarta.mail.*Exception- y nada mas. Un catch de Exception
     * o de RuntimeException se tragaria tambien los fallos de base de datos o
     * los de programacion, que SI deben tumbar el registro y verse.
     *
     * @return true si el correo salio; false si el envio fallo.
     */
    private boolean enviarCorreoDeVerificacion(String toEmail, String nombre, String token) {
        String link = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/auth/confirm")
                .queryParam("token", token)
                .toUriString();

        // HashMap y no Map.of porque el nombre puede venir vacio y Map.of no
        // acepta valores nulos; la plantilla ya sabe saludar sin nombre.
        Map<String, Object> datos = new HashMap<>();
        datos.put("nombre", nombre);
        datos.put("enlace", link);
        datos.put("minutos", TOKEN_EXPIRATION_MINUTES);

        try {
            emailService.enviarCorreo(toEmail, PlantillaDeCorreo.CONFIRMACION_DE_REGISTRO, datos);
            return true;
        } catch (MailException e) {
            // Nivel error y con la excepcion completa: el stack trace lleva la
            // respuesta del servidor SMTP (el "535-5.7.8 Username and Password
            // not accepted" o el "454-4.7.0 Too many login attempts"), que es
            // justo lo que hace falta para saber si hay que rotar la clave,
            // esperar la cuota o revisar la red.
            log.error("No se pudo enviar el correo de confirmacion a {}. La cuenta quedo creada y "
                    + "deshabilitada, con token de verificacion valido por {} minutos; el usuario "
                    + "puede reintentar el registro para generar uno nuevo.",
                    toEmail, TOKEN_EXPIRATION_MINUTES, e);
            return false;
        }
    }


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
     * El "como" no es obvio y por eso hace falta decirlo: no hay endpoint de
     * reenvio, el mecanismo es volver a llenar el formulario de registro con el
     * mismo correo. Mientras la cuenta siga sin confirmar eso no da error de
     * duplicado; la sobreescribe y emite un token nuevo (ver registrarMedico,
     * mas arriba).
     *
     * Igual que en el registro, un fallo de correo se registra y se sigue: la
     * respuesta al usuario no depende de que el SMTP este de buenas.
     */
    private void avisarQueElEnlaceVencio(String toEmail) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("enlaceRegistro", urlDelFrontend + "/register");
        datos.put("minutos", TOKEN_EXPIRATION_MINUTES);
        try {
            emailService.enviarCorreo(toEmail, PlantillaDeCorreo.TOKEN_EXPIRADO, datos);
        } catch (MailException e) {
            log.error("No se pudo avisar a {} de que su enlace de confirmacion vencio. "
                    + "Puede conseguir uno nuevo registrandose otra vez con el mismo correo.", toEmail, e);
        }
    }
}
