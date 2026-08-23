package ues.edu.sv.education.model.mappers;

import ues.edu.sv.education.model.dto.User.UserRequestDto;
import ues.edu.sv.education.model.dto.User.UserResponseDto;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.User;

import java.util.HashSet;

public class UserMapper {
    public static UserResponseDto toDto(User user) {
        Persona persona = user.getPersona();
        return new UserResponseDto(
                user.getUserID(),
                user.getEmail(),
                persona.getNombres() + " " + persona.getApellidos(),
                user.getRoles().stream().map(RoleMapper::toDto).toList(),
                user.getUserType().getName()
        );
    }

    // La persona ya debe existir (guardada) antes de llamar esto: User solo
    // guarda la referencia, no crea su propia identidad. Ver
    // AuthService.createUser / UserService.createUser.
    public static User toEntity(UserRequestDto request, Persona persona) {
        return new User(
                null,
                persona,
                request.email(),
                request.password(),
                false,
                new HashSet<>(),
                null,
                null
        );
    }
}
