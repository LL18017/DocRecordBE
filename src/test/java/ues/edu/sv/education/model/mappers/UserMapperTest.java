/*
package ues.edu.sv.education.model.mappers;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ues.edu.sv.education.model.dto.User.UserRequestDto;
import ues.edu.sv.education.model.dto.User.UserResponseDto;
import ues.edu.sv.education.model.entity.Role;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.entity.UserType;

import java.util.*;

class UserMapperTest {
    @Test
    public void tesUserToDto() {
        Set<Role> roles = Set.of(new Role(1, "Admin"));
        User user = User.builder()
                .UserID(1)
                .name("mario")
                .email("mario@ues.edu.sv")
                .password("12345")
                .roles(roles)
                .userType(new UserType())
                .build();

        UserResponseDto dto = UserMapper.toDto(user);
        Assertions.assertEquals(UserResponseDto.class, dto.getClass());
        Assertions.assertEquals(user.getUserID(), dto.userId());
        Assertions.assertEquals(user.getName(), dto.userName());
        Assertions.assertEquals(user.getEmail(), dto.email());
        Assertions.assertEquals(roles.size(), dto.roles().size());

    }

    @Test
    public void testDtoToEntity() {
        UserRequestDto dto = new UserRequestDto(
                "mario@gmail.com",
                "mario",
                "12345",
                List.of(1, 2)
                , 1
        );
        User entity = UserMapper.toEntity(dto);
        Assertions.assertEquals(User.class, entity.getClass());
        Assertions.assertNull(entity.getUserID());
        Assertions.assertEquals(dto.userName(), entity.getName());
        Assertions.assertEquals(dto.email(), entity.getEmail());
        Assertions.assertEquals(dto.password(), entity.getPassword());
        Assertions.assertEquals(dto.roles().size(), entity.getRoles().size());
    }
}
*/
