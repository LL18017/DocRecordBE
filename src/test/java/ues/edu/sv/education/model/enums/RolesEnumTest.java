/*
package ues.edu.sv.education.model.enums;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

class RolesEnumTest {

    @Test
    void getIdByName() {
        Integer expect = 1;
        String role = "ADMIN";
        Integer id = RolesEnum.getIdByName(role);
        Assertions.assertEquals(expect, id);
    }

    @Test
    void getIdByNameUnExist() {
        String role = "JEFASO";
        Integer id = RolesEnum.getIdByName(role);
        Assertions.assertNull(id);
    }

    @Test
    void fromName() {
        Integer expect = 1;
        String role = "ADMIN";
        RolesEnum enumRole = RolesEnum.fromName(role);
        Assertions.assertEquals(expect, enumRole.getId());
        Assertions.assertEquals(role, enumRole.getName());
    }

    @Test
    void fromNameUnExist() {
        String role = "JEFASO";
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            RolesEnum.fromName(role);
        });


    }

    @Test
    void getAuthority() {
        String authExpect = "ROLE_ADMIN";
        String auth = RolesEnum.ADMIN.getAuthority();
        Assertions
                .assertEquals(authExpect, auth);
    }
}*/
