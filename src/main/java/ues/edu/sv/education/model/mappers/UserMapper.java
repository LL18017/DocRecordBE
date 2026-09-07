package ues.edu.sv.education.model.mappers;

import ues.edu.sv.education.model.dto.User.UserRequestDto;
import ues.edu.sv.education.model.dto.User.UserResponseDto;
import ues.edu.sv.education.model.entity.User;

import java.util.HashSet;

public class UserMapper {

    /**
     * Convierte una entidad User a UserResponseDto.
     *
     * @param user entidad del usuario
     * @return DTO de respuesta
     */
    public static UserResponseDto toDto(User user) {
        return new UserResponseDto(
                user.getUserID(),
                user.getEmail(),
                user.getName(),
                user.getRoles()
                        .stream()
                        .map(RoleMapper::toDto)
                        .toList(),
                user.getUserType().getName()
        );
    }

    /**
     * Convierte un UserRequestDto a una entidad User.
     *
     * Este método está pensado para el proceso de registro.
     * La creación de usuarios pertenece al servicio de autenticación.
     *
     * @param dto datos del usuario
     * @return entidad User
     */
    public static User toEntity(UserRequestDto dto) {
        return new User(
                null,
                dto.userName(),
                dto.email(),
                dto.password(),
                false,
                new HashSet<>(),
                null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * Actualiza los datos administrables de un usuario.
     *
     * La contraseña no se modifica desde el CRUD de usuarios,
     * ya que su gestión corresponde al servicio de autenticación.
     *
     * @param user entidad existente
     * @param dto datos nuevos
     */
    public static void updateEntity(User user, UserRequestDto dto) {
        user.setName(dto.userName());
        user.setEmail(dto.email());
    }
}