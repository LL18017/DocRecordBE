package ues.edu.sv.education.model.mappers;

import ues.edu.sv.education.model.dto.User.UserRequestDto;
import ues.edu.sv.education.model.dto.User.UserResponseDto;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.entity.UserType;

import java.util.HashSet;
import java.util.stream.Collectors;

public class UserMapper {
    public static UserResponseDto toDto(User user) {
        return new UserResponseDto(
                user.getUserID(),
                user.getEmail(),
                user.getName(),
                user.getRoles().stream().map(RoleMapper::toDto).toList(),
                user.getUserType().getName()
        );
    }

    public static User toEntity(UserRequestDto user) {
        return new User(
                null,
                user.userName(),
                user.email(),
                user.password(),
                false,
                new HashSet<>(),
                null
        );
    }
}
