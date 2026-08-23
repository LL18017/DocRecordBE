package ues.edu.sv.education.model.mappers;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ues.edu.sv.education.model.dto.roles.RoleDto;
import ues.edu.sv.education.model.entity.Role;


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
    // La autoridad se recorta con startsWith, no con un substring(5) a ciegas.
    // Con el substring fijo, una autoridad SIN el prefijo se quedaba con los
    // caracteres equivocados: de "ADMIN" salia "" y el id venia nulo.
    void testAutoridadSinPrefijoRoleSigueResolviendoElId() {
        RoleDto dto = RoleMapper.toDto("ADMIN");
        Assertions.assertEquals(1, dto.getId());
        Assertions.assertEquals("ADMIN", dto.getName());
    }

    @Test
    // Y si de verdad no es un rol, tiene que decirlo con la excepcion del
    // dominio. El substring(5) sobre una cadena mas corta que el prefijo
    // reventaba antes con StringIndexOutOfBoundsException, que no explica nada.
    void testAutoridadQueNoEsUnRolLanzaIllegalArgument() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> RoleMapper.toDto("X"));
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