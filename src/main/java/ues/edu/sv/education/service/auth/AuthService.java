package ues.edu.sv.education.service.auth;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

        // Enviar correo de confirmación
        sendVerificationEmail(savedUser.getEmail(), token);

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
                )
        );
    }

    private void sendVerificationEmail(String toEmail, String token) {
        String link = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/auth/confirm")
                .queryParam("token", token)
                .toUriString();
        emailService.sendEmail(toEmail,"Confirma tu registro","Bienvenido. Para activar tu cuenta, haz clic en el siguiente enlace:\n" + link
                + "\n\nEste enlace expira en " + TOKEN_EXPIRATION_MINUTES + " minutos.");
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
            throw new GeneralException("El token ha expirado");
        }

        User user = verificationToken.getUser();
        user.setEnabled(true);
        userRepository.save(user);

        verificationToken.setUsed(true);
        verificationTokenRepository.save(verificationToken);
    }
}
