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
import ues.edu.sv.education.model.dto.User.UserRequestDto;
import ues.edu.sv.education.model.dto.auth.CustomUserDetails;
import ues.edu.sv.education.model.dto.auth.LoginResponseDto;
import ues.edu.sv.education.model.dto.auth.UserLoginDto;
import ues.edu.sv.education.model.dto.User.UserResponseDto;
import ues.edu.sv.education.model.entity.*;
import ues.edu.sv.education.model.enums.EventCodeEnums;
import ues.edu.sv.education.model.enums.EventStatusEnums;
import ues.edu.sv.education.model.mappers.RoleMapper;
import ues.edu.sv.education.model.mappers.UserMapper;
import ues.edu.sv.education.repository.*;
import ues.edu.sv.education.service.EmailService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final UserTypeRepository userTypeRepository;
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
        return new LoginResponseDto(u.getName(), jwtService.generateToken(user), jwtService.generateRefreshToken(user),
                user.getAuthorities().stream().map(a -> RoleMapper.toDto(a.getAuthority())).toList()
        );
    }

    public User createUser(UserRequestDto request) {

        Optional<User> userOptional = userRepository.findByEmailContainingIgnoreCase(request.email());

        User userToSave;

        if (userOptional.isPresent()) {
            User existingUser = userOptional.get();

            if (existingUser.isEnabled()) {
                throw new IllegalStateException("El correo ya está registrado y confirmado");
            }

            // Cuenta no confirmada: se sobreescribe con los datos nuevos
            userToSave = existingUser;
            userToSave.setName(request.userName());
            userToSave.setPassword(passwordEncoder.encode(request.password()));

            // Invalida cualquier token anterior de esta cuenta
            verificationTokenRepository.deleteAllByUser(existingUser);

        } else {
            userToSave = UserMapper.toEntity(request);
            userToSave.setPassword(passwordEncoder.encode(request.password()));
        }

        UserType userType = userTypeRepository.getReferenceById(request.userType());
        userToSave.setUserType(userType);

        Set<Role> roles = request.roles()
                .stream()
                .map(roleRepository::getReferenceById)
                .collect(Collectors.toSet());
        userToSave.setRoles(roles);

        // El usuario queda deshabilitado hasta que confirme el correo
        userToSave.setEnabled(false);

        User savedUser = userRepository.save(userToSave);

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

        return savedUser;
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
