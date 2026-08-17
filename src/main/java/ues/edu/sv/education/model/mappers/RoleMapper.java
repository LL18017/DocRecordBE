package ues.edu.sv.education.model.mappers;

import ues.edu.sv.education.model.dto.roles.RoleDto;
import ues.edu.sv.education.model.entity.Role;
import ues.edu.sv.education.model.enums.RolesEnum;

import java.util.Arrays;

public class RoleMapper {
    public static RoleDto toDto(String name) {
        return new RoleDto(
                RolesEnum.getIdByName(name.substring(5)),
                name
        );
    }

    public static RoleDto toDto(Role role) {
        return new RoleDto(
                RolesEnum.getIdByName(role.getName()),
                role.getName()
        );
    }

    public static Role toEntity(Integer idRole) {
        RolesEnum rol = Arrays.stream(RolesEnum.values()).filter(r -> idRole == r.getId()).findFirst().orElse(null);
        if (rol == null) return null;
        return  new Role(rol.getId(), rol.getName());
    }

    public static Role toEntity(RoleDto role) {
        return new Role(
                RolesEnum.getIdByName(role.getName()),
                role.getName()
        );
    }
}
