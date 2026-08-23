package ues.edu.sv.education.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.User.UserRequestDto;
import ues.edu.sv.education.model.dto.User.UserResponseDto;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.Role;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.mappers.UserMapper;
import ues.edu.sv.education.repository.PersonaRepository;
import ues.edu.sv.education.repository.RoleRepository;
import ues.edu.sv.education.repository.UserRepository;


import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PersonaRepository personaRepository;
    private final RoleRepository roleRepository;
    // El mismo bean que usa /auth/register: Argon2PasswordEncoder, definido en
    // Security/BasicConfiguration.passwordEncoder().
    private final PasswordEncoder passwordEncoder;

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
     * ── PENDIENTE: la cuenta que crea este metodo todavia NO sirve ────────
     * Nace con enabled = false -lo fija UserMapper.toEntity- y NO se le emite
     * fila en verification_token, asi que no existe enlace de confirmacion
     * que abrir y /auth/login le responde siempre "Usuario no ha confirmado
     * su cuenta aun". Es decir: el administrador crea la cuenta, el cifrado ya
     * es correcto, y aun asi nadie puede entrar con ella.
     *
     * NO se arregla aqui porque falta una decision de producto, no codigo:
     * hay que definir quien da de alta al personal y si un medico dueno de su
     * clinica puede registrar a sus enfermeras. Segun como se responda, la
     * salida es una de estas y son incompatibles entre si:
     *
     *   a) emitir el token de verificacion y mandar el correo, igual que
     *      AuthService.registrarMedico (ahi esta el patron completo: generar
     *      el UUID, guardar VerificationToken y llamar al EmailService), o
     *   b) crear la cuenta ya habilitada, porque el alta la hace un
     *      administrador identificado y confirmar el correo sobra.
     *
     * Quien lo retome: el metodo esta a un paso de (a). Falta inyectar
     * VerificationTokenRepository y EmailService, y repetir el bloque de
     * AuthService.registrarMedico; el cifrado, que era el requisito previo,
     * ya esta hecho.
     */
    public UserResponseDto createUser(UserRequestDto userRequest) {
        String[] nombreDividido = dividirNombreCompleto(userRequest.userName());
        Persona persona = personaRepository.save(
                Persona.builder()
                        .nombres(nombreDividido[0])
                        .apellidos(nombreDividido[1])
                        .build()
        );
        User user = UserMapper.toEntity(userRequest, persona,
                passwordEncoder.encode(userRequest.password()));
        userRepository.save(user);
        return UserMapper.toDto(user);
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
