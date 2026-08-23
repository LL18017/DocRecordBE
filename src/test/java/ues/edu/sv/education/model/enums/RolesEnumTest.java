package ues.edu.sv.education.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de {@link RolesEnum}.
 *
 * Este archivo estaba comentado por completo (bloque de la linea 1 a la 52), asi
 * que no verificaba nada. Se reactiva y se amplia.
 *
 * El caso que mas importa aqui es {@link #elCatalogoDeRolesEsElDelDominioMedico()}:
 * el enum llego a declarar DIRECTOR, PROFESOR y ESTUDIANTE, heredados del proyecto
 * academico del que se reutilizo este backend. Una prueba que solo comprobara
 * ADMIN habria seguido en verde con esos valores dentro.
 */
class RolesEnumTest {

    // ---------------------------------------------------------------- catalogo

    @Test
    @DisplayName("el catalogo declara exactamente los cuatro roles del dominio medico")
    void elCatalogoDeRolesEsElDelDominioMedico() {
        Set<String> declarados = Arrays.stream(RolesEnum.values())
                .map(RolesEnum::getName)
                .collect(Collectors.toSet());

        assertEquals(
                Set.of("ADMIN", "MEDICO", "ENFERMERA", "PACIENTE", "ROTO_A_PROPOSITO_PARA_PROBAR_CI"),
                declarados,
                "RolesEnum debe declarar solo los roles de DocRecord. Si aparecen "
                        + "DIRECTOR, PROFESOR o ESTUDIANTE, son restos del proyecto academico previo.");
    }

    @Test
    @DisplayName("ningun rol comparte identificador con otro")
    void losIdentificadoresSonUnicos() {
        long distintos = Arrays.stream(RolesEnum.values()).map(RolesEnum::getId).distinct().count();

        assertEquals(RolesEnum.values().length, distintos,
                "Dos roles con el mismo id harian que RoleMapper.toEntity devuelva el rol equivocado.");
    }

    @Test
    @DisplayName("ningun nombre de rol se repite")
    void losNombresSonUnicos() {
        long distintos = Arrays.stream(RolesEnum.values()).map(RolesEnum::getName).distinct().count();

        assertEquals(RolesEnum.values().length, distintos);
    }

    // ------------------------------------------------------------ getIdByName

    @ParameterizedTest(name = "getIdByName(\"{0}\") = {1}")
    @CsvSource({
            "ADMIN,     1",
            "MEDICO,    2",
            "ENFERMERA, 3",
            "PACIENTE,  4"
    })
    @DisplayName("resuelve el id de cada rol del catalogo")
    void resuelveElIdDeCadaRol(String nombre, Integer idEsperado) {
        assertEquals(idEsperado, RolesEnum.getIdByName(nombre));
    }

    @ParameterizedTest(name = "getIdByName(\"{0}\") ignora mayusculas")
    @ValueSource(strings = {"admin", "Admin", "aDmIn", "ADMIN"})
    @DisplayName("la busqueda por nombre no distingue mayusculas")
    void laBusquedaPorNombreIgnoraMayusculas(String variante) {
        assertEquals(1, RolesEnum.getIdByName(variante));
    }

    @ParameterizedTest(name = "getIdByName(\"{0}\") lanza")
    @ValueSource(strings = {"JEFASO", "DIRECTOR", "PROFESOR", "ESTUDIANTE", ""})
    @DisplayName("lanza para un rol que no existe, incluidos los heredados, en vez de devolver null")
    void getIdByNameLanzaParaRolInexistente(String nombre) {
        // Antes devolvia null. Ese null no lo miraba ningun llamador: se metia
        // tal cual en el RoleDto y salia por la API como `id: null`, hasta que
        // el frontend tuvo que defenderse de el. Un nombre que no es un rol es
        // un defecto de programacion y tiene que verse aqui.
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> RolesEnum.getIdByName(nombre));

        assertEquals("Rol no válido: " + nombre, error.getMessage(),
                "El mensaje debe nombrar el valor rechazado; sin el no se sabe que llego.");
    }

    @ParameterizedTest
    @EnumSource(RolesEnum.class)
    @DisplayName("getIdByName y fromName aceptan exactamente lo mismo")
    void getIdByNameYFromNameCoinciden(RolesEnum rol) {
        // Las dos busquedas se diferenciaban solo en como fallaban, y esa
        // diferencia era la que permitia elegir la que no molestaba. Deben
        // seguir siendo la misma pregunta.
        assertEquals(RolesEnum.fromName(rol.getName()).getId(), RolesEnum.getIdByName(rol.getName()));
    }

    // -------------------------------------------------------------- fromName

    @ParameterizedTest
    @EnumSource(RolesEnum.class)
    @DisplayName("fromName devuelve el mismo enum del que salio el nombre")
    void fromNameEsInversaDeGetName(RolesEnum rol) {
        assertEquals(rol, RolesEnum.fromName(rol.getName()));
    }

    @Test
    @DisplayName("fromName lanza excepcion para un rol inexistente en vez de devolver null")
    void fromNameLanzaExcepcionParaRolInexistente() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> RolesEnum.fromName("JEFASO"));

        // El mensaje llega al cliente de la API; debe nombrar el rol rechazado.
        assertEquals("Rol no válido: JEFASO", error.getMessage());
    }

    // ------------------------------------------------------------- authority

    @ParameterizedTest
    @EnumSource(RolesEnum.class)
    @DisplayName("la autoridad de Spring Security lleva el prefijo ROLE_")
    void laAutoridadLlevaPrefijoRole(RolesEnum rol) {
        // Spring Security exige el prefijo ROLE_ para que hasRole() funcione;
        // sin el, @PreAuthorize("hasRole('MEDICO')") nunca coincide.
        assertEquals("ROLE_" + rol.getName(), rol.getAuthority());
    }

    @Test
    @DisplayName("todas las autoridades son distintas entre si")
    void lasAutoridadesSonDistintas() {
        long distintas = EnumSet.allOf(RolesEnum.class).stream()
                .map(RolesEnum::getAuthority)
                .distinct()
                .count();

        assertEquals(RolesEnum.values().length, distintas);
    }
}
