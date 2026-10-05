package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.enums.RolesEnum;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un medico o una enfermera necesita al menos una sede.
 *
 * Sin ninguna, la cuenta inicia sesion y se queda en la pantalla de seleccion
 * de clinica sin poder entrar a trabajar. Por eso a quien no tiene sede el rol
 * clinico se le da junto con la primera, y la ultima no se le puede quitar.
 */
class SedeObligatoriaParaPersonalIT extends PruebaClinica {

    private String admin;
    private int sede;

    @BeforeEach
    void prepararAdministradorYSede() throws Exception {
        admin = tokenDeAdministrador();
        sede = crearSede(admin);
    }

    private User cuentaSinRolNiSede() {
        Persona persona = personas.saveAndFlush(Persona.builder()
                .nombres("Personal").apellidos("Sin Sede")
                .build());
        return usuarios.saveAndFlush(User.builder()
                .persona(persona)
                .email(correoUnico("sede.obligatoria"))
                .password(encoder.encode(CLAVE))
                .enabled(true)
                .roles(new HashSet<>())
                .build());
    }

    private String rutaDeRol(User user, RolesEnum rol) {
        return "/user/" + user.getUserID() + "/role/" + rol.getId();
    }

    @Test
    @DisplayName("dar ENFERMERA a una cuenta sin sede y sin indicarla da 400, y no deja el rol puesto")
    void enfermeraSinSedeSeRechaza() throws Exception {
        User cuenta = cuentaSinRolNiSede();

        mockMvc.perform(post(rutaDeRol(cuenta, RolesEnum.ENFERMERA))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());

        assertTrue(usuarios.findById(cuenta.getUserID()).orElseThrow().getRoles().isEmpty());
    }

    @Test
    @DisplayName("con la sede, el rol se da y la sede queda asignada en el mismo paso")
    void conLaSedeQuedanRolYSede() throws Exception {
        User cuenta = cuentaSinRolNiSede();

        String cuerpo = mockMvc.perform(post(rutaDeRol(cuenta, RolesEnum.ENFERMERA))
                        .param("clinicaId", String.valueOf(sede))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, json.readTree(cuerpo).get("sedes").asInt());

        JsonNode sedes = json.readTree(mockMvc.perform(get("/user/" + cuenta.getUserID() + "/clinicas")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, sedes.size());
        assertEquals(sede, sedes.get(0).get("clinicaId").asInt());
    }

    @Test
    @DisplayName("a quien ya tiene una sede no se le vuelve a pedir")
    void conSedePreviaNoSePide() throws Exception {
        User cuenta = cuentaSinRolNiSede();
        mockMvc.perform(post("/user/" + cuenta.getUserID() + "/clinica/" + sede)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(post(rutaDeRol(cuenta, RolesEnum.ENFERMERA))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN no es un rol clinico: no exige sede")
    void adminNoExigeSede() throws Exception {
        User cuenta = cuentaSinRolNiSede();

        mockMvc.perform(post(rutaDeRol(cuenta, RolesEnum.ADMIN))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("la unica sede de una enfermera no se le puede quitar; con otra asignada, si")
    void laUnicaSedeNoSeQuita() throws Exception {
        User cuenta = cuentaSinRolNiSede();
        mockMvc.perform(post(rutaDeRol(cuenta, RolesEnum.ENFERMERA))
                        .param("clinicaId", String.valueOf(sede))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/user/" + cuenta.getUserID() + "/clinica/" + sede)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());

        int otra = crearSede(admin);
        mockMvc.perform(post("/user/" + cuenta.getUserID() + "/clinica/" + otra)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/user/" + cuenta.getUserID() + "/clinica/" + sede)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("el listado dice cuantas sedes tiene cada cuenta, cero incluido")
    void elListadoTraeLasSedes() throws Exception {
        User sinSede = cuentaSinRolNiSede();

        JsonNode lista = json.readTree(mockMvc.perform(get("/user/all")
                        .param("inicio", "0").param("fin", "1000")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        JsonNode fila = null;
        for (JsonNode u : lista) {
            if (u.get("userId").asInt() == sinSede.getUserID()) fila = u;
        }
        assertTrue(fila != null, "la cuenta debe salir en el listado");
        assertEquals(0, fila.get("sedes").asInt());
    }
}
