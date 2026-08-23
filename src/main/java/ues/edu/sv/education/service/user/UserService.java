package ues.edu.sv.education.service.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.User.AltaUsuarioResponseDto;
import ues.edu.sv.education.model.dto.User.UserRequestDto;
import ues.edu.sv.education.model.dto.User.UserResponseDto;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.Role;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.entity.VerificationToken;
import ues.edu.sv.education.model.enums.RolesEnum;
import ues.edu.sv.education.model.mappers.UserMapper;
import ues.edu.sv.education.repository.PersonaRepository;
import ues.edu.sv.education.repository.RoleRepository;
import ues.edu.sv.education.repository.UserRepository;
import ues.edu.sv.education.repository.VerificationTokenRepository;
import ues.edu.sv.education.service.EmailService;
import ues.edu.sv.education.service.PlantillaDeCorreo;
import ues.edu.sv.education.service.auth.AdminAutenticado;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PersonaRepository personaRepository;
    private final RoleRepository roleRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;
    private final AdminAutenticado adminAutenticado;
    // El mismo bean que usa /auth/register: Argon2PasswordEncoder, definido en
    // Security/BasicConfiguration.passwordEncoder().
    private final PasswordEncoder passwordEncoder;
    private static final long TOKEN_EXPIRATION_MINUTES = 5;

    public List<UserResponseDto> getAll(Integer inicio, Integer fin) {
        Pageable pageable = PageRequest.of(inicio, fin);
        return userRepository.findAll(pageable).stream().map(UserMapper::toDto).toList();
    }

    public UserResponseDto addRole(int userID, int roleID) {
        Optional<User> userOpt = userRepository.findById(userID);
        if (userOpt.isEmpty()) {
            throw new NoResourceFoundException("No se encontro el usuario con id: " + userID, "404");
        }
        Optional<Role> roleOpt = roleRepository.findById(roleID);
        if (roleOpt.isEmpty()) {
            throw new NoResourceFoundException("No se encontro el rol con id: " + roleID, "404");
        }
        User user = userOpt.get();
        Role role = roleOpt.get();
        if (user.getRoles().contains(role)) {
            throw new GeneralException("El usuario ya cuenta con este rol", "409");
        }
        user.getRoles().add(role);
        userRepository.save(user);
        return UserMapper.toDto(user);
    }

    /**
     * Alta directa de una cuenta por un administrador (POST /user).
     *
     * ── La contrasena se cifra ANTES de guardarla ─────────────────────────
     * Este metodo guardaba la contrasena tal como llegaba en el cuerpo de la
     * peticion: la columna users.password quedaba con el texto legible, y se
     * comprobo en la base de desarrollo leyendo "Docrecord2026!" sin cifrar.
     * Cualquiera con acceso de lectura a la tabla -un SELECT, un volcado, una
     * copia de seguridad mal guardada- se llevaba las credenciales de todo el
     * personal de un expediente clinico; y como la gente reusa contrasenas, el
     * dano no se habria quedado en este sistema. Ahora pasa por el mismo
     * PasswordEncoder (argon2) que /auth/register, que es el unico flujo que
     * lo hacia bien.
     *
     * ── Confirmacion por correo, igual que /auth/register ─────────────────
     * Nace con enabled = false -lo fija UserMapper.toEntity- y ahora si se le
     * emite un VerificationToken y se manda el correo de confirmacion, mismo
     * patron que AuthService.registrarMedico. Si el correo falla (MailException),
     * la cuenta se crea de todos modos -no depender de un SMTP externo para dar
     * de alta personal- y correoDeVerificacionEnviado sale en false para que el
     * cliente lo sepa. En ese caso el administrador tiene una salida aparte:
     * asignarContrasena tambien habilita la cuenta, sin depender de que el
     * correo llegue.
     */
    @Transactional
    public AltaUsuarioResponseDto createUser(UserRequestDto userRequest) {
        String[] nombreDividido = dividirNombreCompleto(userRequest.userName());
        Persona persona = personaRepository.save(
                Persona.builder()
                        .nombres(nombreDividido[0])
                        .apellidos(nombreDividido[1])
                        .build()
        );
        User user = UserMapper.toEntity(userRequest, persona,
                passwordEncoder.encode(userRequest.password()));
        User savedUser = userRepository.save(user);

        String token = UUID.randomUUID().toString();
        verificationTokenRepository.save(new VerificationToken(
                null, token, savedUser, LocalDateTime.now().plusMinutes(TOKEN_EXPIRATION_MINUTES), false));

        boolean correoEnviado = enviarCorreoDeVerificacion(
                savedUser.getEmail(), userRequest.userName(), token);

        UserResponseDto dto = UserMapper.toDto(savedUser);
        return new AltaUsuarioResponseDto(
                dto.userId(), dto.email(), dto.userName(), dto.roles(), correoEnviado);
    }

    // Mismo patron que AuthService.enviarCorreoDeVerificacion: solo se atrapa
    // MailException -un problema de SMTP no debe tumbar el alta de la cuenta-
    // y se deja constancia en el log de cualquier otra falla real.
    private boolean enviarCorreoDeVerificacion(String correoDestino, String nombre, String token) {
        String enlace = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/auth/confirm")
                .queryParam("token", token)
                .toUriString();
        Map<String, Object> datos = new HashMap<>();
        datos.put("nombre", nombre);
        datos.put("enlace", enlace);
        datos.put("minutos", TOKEN_EXPIRATION_MINUTES);
        try {
            emailService.enviarCorreo(correoDestino, PlantillaDeCorreo.CONFIRMACION_DE_REGISTRO, datos);
            return true;
        } catch (MailException e) {
            log.error("No se pudo enviar el correo de confirmacion a {}", correoDestino, e);
            return false;
        }
    }

    /**
     * Un administrador asigna una contrasena nueva a otro usuario (o a si
     * mismo) por POST /user/{userId}/password.
     *
     * ── Tambien habilita la cuenta ─────────────────────────────────────────
     * Si la cuenta nacio deshabilitada porque el correo de confirmacion nunca
     * salio (createUser con correoDeVerificacionEnviado = false), esta es la
     * salida: un administrador identificado asignando la contrasena a mano es
     * al menos tan fuerte como un enlace de correo, asi que no tiene sentido
     * dejar la cuenta atrapada sin acceso. Se borra ademas cualquier token de
     * verificacion pendiente -ya no aplica, la cuenta quedo habilitada aqui-.
     *
     * ── No se cierran sesiones existentes ──────────────────────────────────
     * JwtService no tiene mecanismo de revocacion/lista negra (ver DRS-79 /
     * DRS-8): los tokens ya emitidos son validos hasta que expiran por su
     * propio "exp", sin excepcion. Construir revocacion aqui -una lista negra,
     * un numero de version por usuario que JwtFilter tendria que consultar en
     * cada peticion autenticada- es infraestructura nueva que toca el camino
     * caliente de todo el sistema, desproporcionada para esta tarea. La
     * mitigacion real es que el access token dura poco (15 min).
     *
     * ── Un administrador no puede tocar la contrasena de OTRO administrador ─
     * Si pudiera, cualquier cuenta ADMIN comprometida se convierte en control
     * total sobre todas las demas cuentas ADMIN: no es conveniencia, es
     * escalacion de privilegios. Un administrador SI puede asignarse una
     * contrasena a si mismo (mismo criterio que "cambiar mi propia
     * contrasena", no un ataque a un tercero).
     */
    @Transactional
    public UserResponseDto asignarContrasena(int userId, String nuevaContrasena) {
        User administrador = adminAutenticado.exigir();

        User objetivo = userRepository.findById(userId)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontro el usuario con id: " + userId, "404"));

        boolean objetivoEsAdmin = objetivo.getRoles().stream()
                .anyMatch(rol -> RolesEnum.ADMIN.getName().equalsIgnoreCase(rol.getName()));
        if (objetivoEsAdmin && !objetivo.getUserID().equals(administrador.getUserID())) {
            throw new GeneralException(
                    "Un administrador no puede asignar la contrasena de otro administrador", "403");
        }

        objetivo.setPassword(passwordEncoder.encode(nuevaContrasena));
        objetivo.setEnabled(true);
        userRepository.save(objetivo);
        verificationTokenRepository.deleteAllByUser(objetivo);

        return UserMapper.toDto(objetivo);
    }

    public void deleteUser(String userEmail) {
        User user = userRepository.findByEmailContainingIgnoreCase(userEmail).orElseThrow(()-> new NoResourceFoundException("Usuario no encontrado","404"));
        userRepository.delete(user);
    }

    // Ver el mismo helper en AuthService: userName sigue siendo un solo campo
    // hasta que este endpoint tambien pida nombres/apellidos por separado.
    private String[] dividirNombreCompleto(String nombreCompleto) {
        String limpio = nombreCompleto.trim();
        int espacio = limpio.indexOf(' ');
        if (espacio < 0) {
            return new String[]{limpio, limpio};
        }
        String nombres = limpio.substring(0, espacio);
        String apellidos = limpio.substring(espacio + 1).trim();
        return new String[]{nombres, apellidos.isEmpty() ? nombres : apellidos};
    }
}
