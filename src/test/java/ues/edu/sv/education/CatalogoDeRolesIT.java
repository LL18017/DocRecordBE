package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import ues.edu.sv.education.model.enums.RolesEnum;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El catalogo de roles es cerrado, y quien lo cierra es la base.
 *
 * `role.name` nacio en V1 como `varchar(255)` sin NOT NULL, sin UNIQUE y sin
 * CHECK, o sea texto libre, cuando en realidad es un enum de cuatro valores.
 * Un nombre que RolesEnum no conociera hacia que RoleMapper devolviera
 * `id: null`, y ese null llegaba al cliente: el frontend tuvo que escribir
 * `rol.id ?? rol.name` y una guarda para que `nombre.toUpperCase()` no tumbara
 * la pantalla de usuarios.
 *
 * Estas pruebas verifican el arreglo donde esta la causa y no donde se notaba
 * el sintoma: se le pide a PostgreSQL que acepte los roles invalidos que antes
 * aceptaba, y tiene que negarse. Van por JDBC y no por el repositorio a
 * proposito -- una anotacion de JPA solo protege lo que pasa por Hibernate, y
 * por esta tabla pasan tambien las migraciones, data.sql y cualquiera con un
 * psql abierto. Lo que se quiere comprobar es la restriccion del motor.
 *
 * Si se quita el CHECK, el UNIQUE o el NOT NULL de V7, estas pruebas se ponen
 * en rojo; se comprobo quitandolos uno a uno.
 */
class CatalogoDeRolesIT extends PruebaClinica {

    @Autowired private JdbcTemplate jdbc;

    // ─────────────────────────────────────────── lo que la base debe rechazar

    @Test
    @DisplayName("la base rechaza un rol que no esta en el catalogo")
    void laBaseRechazaUnRolFueraDelCatalogo() {
        DataIntegrityViolationException error = assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO public.role (role_id, name) VALUES (90, 'JEFASO')"));

        // El nombre de la restriccion importa: sin el, esta prueba tambien
        // pasaria si la fila se rechazara por cualquier otro motivo.
        assertTrue(mensajeCompleto(error).contains("role_name_del_catalogo"),
                "Debe rechazarlo el CHECK del catalogo. Mensaje: " + mensajeCompleto(error));

        assertEquals(0, filasConId(90), "La fila no debio quedar guardada");
    }

    @Test
    @DisplayName("la base rechaza un rol sin nombre")
    void laBaseRechazaUnRolSinNombre() {
        // Un rol con nombre nulo producia la autoridad "ROLE_null" en
        // CustomUserDetails, que no coincide con ningun @PreAuthorize y por eso
        // se manifestaba como un 403 sin explicacion.
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO public.role (role_id, name) VALUES (91, NULL)"));

        assertEquals(0, filasConId(91), "La fila no debio quedar guardada");
    }

    @Test
    @DisplayName("la base rechaza dos roles con el mismo nombre")
    void laBaseRechazaUnNombreDeRolRepetido() {
        // Dos filas 'ADMIN' con ids distintos son dos roles que el codigo no
        // puede distinguir: getIdByName siempre devuelve el primero, asi que
        // los usuarios asignados al segundo se mapearian al primero.
        DataIntegrityViolationException error = assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO public.role (role_id, name) VALUES (92, 'ADMIN')"));

        assertTrue(mensajeCompleto(error).contains("role_name_unico"),
                "Debe rechazarlo el UNIQUE del nombre. Mensaje: " + mensajeCompleto(error));

        assertEquals(0, filasConId(92), "La fila no debio quedar guardada");
    }

    // ──────────────────────────────────────────── tabla y enum dicen lo mismo

    @Test
    @DisplayName("la tabla role y RolesEnum declaran exactamente los mismos roles")
    void laTablaYElEnumCoinciden() {
        // El CHECK de V7 lleva los cuatro nombres escritos a mano porque SQL no
        // puede leer el enum. Esta prueba es la costura entre los dos: si
        // alguien agrega un quinto rol al enum sin migracion, o cambia un
        // nombre en la migracion sin tocar el enum, se pone en rojo.
        Map<Integer, String> enLaBase = new HashMap<>();
        jdbc.queryForList("SELECT role_id, name FROM public.role").forEach(
                fila -> enLaBase.put((Integer) fila.get("role_id"), (String) fila.get("name")));

        Map<Integer, String> enElEnum = Arrays.stream(RolesEnum.values())
                .collect(Collectors.toMap(RolesEnum::getId, RolesEnum::getName));

        assertEquals(enElEnum, enLaBase,
                "La tabla `role` (V7) y RolesEnum tienen que decir lo mismo: el mapper "
                        + "resuelve el id buscando el nombre de la tabla dentro del enum.");
    }

    // ───────────────────────────────────────────── lo que ve el frontend

    @Test
    @DisplayName("GET /roles/all no devuelve ningun id nulo")
    void elCatalogoQueVeElFrontendNoTraeIdsNulos() throws Exception {
        // Es exactamente la respuesta contra la que el frontend se blindo.
        String cuerpo = mockMvc.perform(get("/roles/all")
                        .header("Authorization", bearer(tokenDeMedico())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode roles = json.readTree(cuerpo);

        assertEquals(RolesEnum.values().length, roles.size(),
                "El catalogo debe traer los cuatro roles del dominio");

        for (JsonNode rol : roles) {
            String nombre = rol.get("name").asText();
            assertTrue(rol.hasNonNull("id"),
                    "El rol '" + nombre + "' llego con id nulo, que es justo lo que el "
                            + "frontend tuvo que parchear con `rol.id ?? rol.name`");
            assertEquals(RolesEnum.getIdByName(nombre), rol.get("id").asInt(),
                    "El id del rol '" + nombre + "' no es el que dice RolesEnum");
        }
    }

    // ──────────────────────────────────────────────────────────────── apoyo

    private int filasConId(int roleId) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM public.role WHERE role_id = ?", Integer.class, roleId);
        assertNotNull(total);
        return total;
    }

    /** El nombre de la restriccion vive en la causa, no en el mensaje de Spring. */
    private String mensajeCompleto(Throwable error) {
        StringBuilder texto = new StringBuilder();
        for (Throwable t = error; t != null; t = t.getCause()) {
            texto.append(t.getMessage()).append(' ');
        }
        return texto.toString();
    }
}
