package ues.edu.sv.education.model.mappers;

import jdk.swing.interop.SwingInterOpUtils;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ues.edu.sv.education.model.dto.roles.RoleDto;
import ues.edu.sv.education.model.entity.Role;
import ues.edu.sv.education.model.enums.RolesEnum;

import static org.junit.jupiter.api.Assertions.*;


class RoleMapperTest {

    @Test
    //TEST PARA CONVERTIR A UN dto dado un role
    void testRoleNameToDto() {
        String name = "ROLE_ADMIN";
        RoleDto DTO = RoleMapper.toDto(name);
        Assertions.assertEquals(name, DTO.getName());
        Assertions.assertEquals(1, DTO.getId());
    }

    @Test
    void testRoleToDto() {
        Role role=new Role(1,"ADMIN");
        RoleDto DTO = RoleMapper.toDto(role);
        Assertions.assertEquals(Role.class, role.getClass());
        Assertions.assertEquals(1, DTO.getId());
        Assertions.assertEquals("ADMIN", DTO.getName());
    }

    @Test
    void testDtoToEntity() {
        RoleDto dto=new RoleDto(1,"ADMIN");
        Role entity=RoleMapper.toEntity(dto);
        Assertions.assertEquals(Role.class, entity.getClass());
    }

    @Test
    void testRoleIdToEntity() {
        // Caso éxito
        Integer id = 1;
        Role entity = RoleMapper.toEntity(id);
        Assertions.assertNotNull(entity);
        Assertions.assertEquals(Role.class, entity.getClass());
        Assertions.assertEquals(1, entity.getRoleId());

        // Caso null — id inexistente
        Role nullEntity = RoleMapper.toEntity(8);
        Assertions.assertNull(nullEntity);

    }
}