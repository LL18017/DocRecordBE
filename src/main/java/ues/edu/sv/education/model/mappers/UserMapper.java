package ues.edu.sv.education.model.mappers;

import ues.edu.sv.education.model.dto.User.UserRequestDto;
import ues.edu.sv.education.model.dto.User.UserResponseDto;
import ues.edu.sv.education.model.entity.User;

import java.util.stream.Collectors;

public class UserMapper {
    public static UserResponseDto toDto(User user) {
        return new UserResponseDto(
                user.getUserID(),
                user.getEmail(),
                user.getName(),
                user.getRoles().stream().map(RoleMapper::toDto).toList()
        );
    }

    public static User toEntity(UserRequestDto user) {
        return new User(
                null,
                user.userName(),
                user.email(),
                user.password(),
                user.roles().stream().map(RoleMapper::toEntity).collect(Collectors.toSet())
        );
    }
}
