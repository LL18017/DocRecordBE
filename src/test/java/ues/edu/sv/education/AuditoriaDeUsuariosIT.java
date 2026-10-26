package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.enums.RolesEnum;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-05, criterio 5: la auditoria de usuarios registra quien y cuando hizo
 * altas, bajas o cambios de estado, y cambios de rol.
 *
 * Se comprueba leyendo el historial por la API, que es como lo consulta la
 * pantalla, y no mirando la tabla.
 */
class AuditoriaDeUsuariosIT extends PruebaClinica {

    private String correoAdmin;

    /** Un administrador con correo conocido, para poder afirmar QUIEN hizo cada cosa. */
    private String tokenDeAdminConocido() throws Exception {
        correoAdmin = correoUnico("auditoria.admin");
        Persona persona = personas.saveAndFlush(Persona.builder()
                .nombres("Admin").apellidos("Auditor").build());
        usuarios.saveAndFlush(User.builder()
                .persona(persona)
                .email(correoAdmin)
                .password(encoder.encode(CLAVE))
                .enabled(true)
                .roles(new HashSet<>(Set.of(roles.getReferenceById(RolesEnum.ADMIN.getId()))))
                .build());

        String cuerpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(correoAdmin, CLAVE)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("token").asText();
    }

    private int altaDeUsuario(String tokenAdmin, String correo) throws Exception {
        mockMvc.perform(post("/user")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","userName":"Usuario Auditado","password":"%s"}
                                """.formatted(correo, CLAVE)))
                .andExpect(status().isCreated());
        return usuarios.findByEmailIgnoreCase(correo).orElseThrow().getUserID();
    }

    private JsonNode bitacora(String tokenAdmin, int userId) throws Exception {
        String cuerpo = mockMvc.perform(get("/user/{id}/bitacora", userId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    @Test
    @DisplayName("el alta, el cambio de estado y los cambios de rol quedan con quien y cuando")
    void registraAltaEstadoYRoles() throws Exception {
        String token = tokenDeAdminConocido();
        int userId = altaDeUsuario(token, correoUnico("auditoria.objetivo"));

        mockMvc.perform(patch("/user/{id}/estado", userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/user/{id}/estado", userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":true}"))
                .andExpect(status().isOk());

        // El rol de ADMIN no pide ficha ni sede, asi que es el caso minimo.
        int rolAdmin = RolesEnum.ADMIN.getId();
        mockMvc.perform(post("/user/{id}/role/{rol}", userId, rolAdmin)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/user/{id}/role/{rol}", userId, rolAdmin)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        JsonNode historial = bitacora(token, userId);

        // Lo mas reciente primero.
        String[] esperado = {"ROL_RETIRADO", "ROL_ASIGNADO", "ACTIVACION", "DESACTIVACION", "ALTA"};
        assertEquals(esperado.length, historial.size(), historial.toString());
        for (int i = 0; i < esperado.length; i++) {
            JsonNode fila = historial.get(i);
            assertEquals(esperado[i], fila.get("accion").asText());
            assertEquals(correoAdmin, fila.get("realizadoPor").asText(),
                    "quien lo hizo sale del token del administrador");
            assertFalse(fila.get("realizadoEn").isNull(), "cada accion lleva su fecha");
        }
        assertEquals("Rol ADMIN", historial.get(0).get("detalle").asText());
    }

    @Test
    @DisplayName("una accion rechazada no deja huella")
    void unaAccionRechazadaNoDejaHuella() throws Exception {
        String token = tokenDeAdminConocido();
        int userId = altaDeUsuario(token, correoUnico("auditoria.rechazada"));

        // Quitar un rol que no tiene: 404, y nada que registrar.
        mockMvc.perform(delete("/user/{id}/role/{rol}", userId, RolesEnum.ADMIN.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        JsonNode historial = bitacora(token, userId);
        assertEquals(1, historial.size(), "solo el alta: " + historial);
        assertEquals("ALTA", historial.get(0).get("accion").asText());
    }

    @Test
    @DisplayName("solo un administrador consulta el historial; un usuario inexistente da 404")
    void soloElAdministradorConsulta() throws Exception {
        String token = tokenDeAdminConocido();
        int userId = altaDeUsuario(token, correoUnico("auditoria.permiso"));

        mockMvc.perform(get("/user/{id}/bitacora", userId)
                        .header("Authorization", "Bearer " + tokenDeMedico()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/user/{id}/bitacora", 99_999_999)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        assertNotNull(bitacora(token, userId));
    }
}
