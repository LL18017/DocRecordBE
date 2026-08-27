package ues.edu.sv.education.model.mappers;

import ues.edu.sv.education.model.dto.roles.RoleDto;
import ues.edu.sv.education.model.dto.userType.UserTypeResponseDto;
import ues.edu.sv.education.model.entity.Role;
import ues.edu.sv.education.model.entity.UserType;
import ues.edu.sv.education.model.enums.RolesEnum;

import java.util.Arrays;
import java.util.List;

public class UserTypeMapper {
    public static UserTypeResponseDto toDto(UserType userType) {
        return new UserTypeResponseDto(
               userType.getUserTypeID(),
                userType.getName()
        );
    }

}
