package ues.edu.sv.education.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.User.UserRequestDto;
import ues.edu.sv.education.model.dto.User.UserResponseDto;
import ues.edu.sv.education.model.dto.auth.CustomUserDetails;
import ues.edu.sv.education.model.entity.Role;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.mappers.UserMapper;
import ues.edu.sv.education.repository.RoleRepository;
import ues.edu.sv.education.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Obtiene una lista paginada de usuarios.
     *
     * @param inicio número de página
     * @param fin cantidad de elementos por página
     * @return lista de usuarios
     */
    public List<UserResponseDto> getAll(Integer inicio, Integer fin) {
        Pageable pageable = PageRequest.of(inicio, fin);

        return userRepository.findAll(pageable)
                .stream()
                .map(UserMapper::toDto)
                .toList();
    }
    /**
     * Obtiene todos los usuarios registrados.
     *
     * @return lista de todos los usuarios
     */
    public List<UserResponseDto> findAll() {
        return userRepository.findAll()
                .stream()
                .map(UserMapper::toDto)
                .toList();
    }

    /**
     * Obtiene un usuario por su identificador.
     *
     * @param userId identificador del usuario
     * @return usuario encontrado
     * @throws NoResourceFoundException si el usuario no existe
     */
    public UserResponseDto getById(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new NoResourceFoundException(
                                "No se encontró el usuario con id: " + userId,
                                "404"
                        )
                );

        return UserMapper.toDto(user);
    }

    /**
     * Actualiza los datos de un usuario.
     *
     * La creación del usuario y el manejo de autenticación
     * pertenecen al servicio de autenticación.
     *
     * @param userId identificador del usuario
     * @param userRequest datos a actualizar
     * @return usuario actualizado
     */
    public UserResponseDto updateUser(
            Integer userId,
            UserRequestDto userRequest
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new NoResourceFoundException(
                                "No se encontró el usuario con id: " + userId,
                                "404"
                        )
                );

        UserMapper.updateEntity(user, userRequest);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);

        return UserMapper.toDto(user);
    }

    /**
     * Elimina un usuario por su identificador.
     *
     * @param userId identificador del usuario
     */
    public void deleteUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new NoResourceFoundException(
                                "No se encontró el usuario con id: " + userId,
                                "404"
                        )
                );

        userRepository.delete(user);
    }

    /**
     * Agrega un rol a un usuario.
     *
     * @param userId identificador del usuario
     * @param roleId identificador del rol
     * @return usuario actualizado
     */
    public UserResponseDto addRole(
            Integer userId,
            Integer roleId
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new NoResourceFoundException(
                                "No se encontró el usuario con id: " + userId,
                                "404"
                        )
                );

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() ->
                        new NoResourceFoundException(
                                "No se encontró el rol con id: " + roleId,
                                "404"
                        )
                );

        if (user.getRoles().contains(role)) {
            throw new GeneralException(
                    "El usuario ya cuenta con este rol",
                    "409"
            );
        }

        user.getRoles().add(role);

        userRepository.save(user);

        return UserMapper.toDto(user);
    }

    public User obtenerUsuarioAutorizado(Integer userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new NoResourceFoundException("Usuario no encontrado","404")
                );

        Integer userTypeId = user.getUserType().getUserTypeID();

        if (userTypeId != 1 && userTypeId != 2) {
            throw new NoResourceFoundException(
                    "El usuario no tiene permisos para administrar clínicas","403"
            );
        }

        return user;
    }
    public User obtenerUsuarioAutorizado() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        CustomUserDetails userDetails =
                (CustomUserDetails) authentication.getPrincipal();

        User user = userRepository.findByEmailContainingIgnoreCase(userDetails.getUsername())
                .orElseThrow(() ->
                        new NoResourceFoundException("Usuario no encontrado","404")
                );

        Integer userTypeId = user.getUserType().getUserTypeID();

        if (userTypeId != 1 && userTypeId != 2) {
            throw new NoResourceFoundException(
                    "El usuario no tiene permisos para administrar clínicas","403"
            );
        }

        return user;
    }
}