package ues.edu.sv.education.model.mappers;

import ues.edu.sv.education.model.dto.roles.RoleRequestDto;
import ues.edu.sv.education.model.dto.roles.RoleResponseDto;
import ues.edu.sv.education.model.entity.Role;
import ues.edu.sv.education.model.enums.RolesEnum;

import java.util.Arrays;

public class RoleMapper {
    public static RoleResponseDto toDto(String name) {
        return new RoleResponseDto(
                RolesEnum.getIdByName(name.substring(5)),
                name
        );
    }

    public static RoleResponseDto toDto(Role role) {
        return new RoleResponseDto(
                role.getRoleId(),
                role.getName()
        );
    }

    public static Role toEntity(Integer idRole) {
        RolesEnum rol = Arrays.stream(RolesEnum.values()).filter(r -> idRole == r.getId()).findFirst().orElse(null);
        if (rol == null) return null;
        return  new Role(rol.getId(), rol.getName());
    }

    public static Role toEntity(RoleResponseDto role) {
        return new Role(
                RolesEnum.getIdByName(role.getName()),
                role.getName()
        );
    }

    public static Role toEntity(RoleRequestDto role) {
        return new Role(null,
                role.getName()
        );
    }
}
