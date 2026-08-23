package ues.edu.sv.education.model.mappers;

import ues.edu.sv.education.model.dto.roles.RoleDto;
import ues.edu.sv.education.model.entity.Role;
import ues.edu.sv.education.model.enums.RolesEnum;

import java.util.Arrays;

public class RoleMapper {

    /** Prefijo que Spring Security exige en toda autoridad para que hasRole() funcione. */
    private static final String PREFIJO_DE_AUTORIDAD = "ROLE_";

    /**
     * DTO a partir de una autoridad de Spring Security ("ROLE_MEDICO").
     *
     * El prefijo se quita con startsWith y no con un substring(5) a ciegas. El
     * substring fijo suponia que TODA autoridad empieza por "ROLE_": con una
     * que no lo hiciera se quedaba con los caracteres equivocados -- y de una
     * mas corta que el prefijo salia un StringIndexOutOfBoundsException, que es
     * el peor sitio donde enterarse.
     *
     * El campo `name` del DTO conserva la autoridad COMPLETA, con prefijo. No
     * es un descuido: asi es como ya viaja en la respuesta de /auth/login y el
     * frontend lo lee tal cual. Recortarlo aqui seria un cambio de contrato
     * disfrazado de limpieza.
     */
    public static RoleDto toDto(String authority) {
        String nombre = authority.startsWith(PREFIJO_DE_AUTORIDAD)
                ? authority.substring(PREFIJO_DE_AUTORIDAD.length())
                : authority;
        return new RoleDto(
                RolesEnum.getIdByName(nombre),
                authority
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
