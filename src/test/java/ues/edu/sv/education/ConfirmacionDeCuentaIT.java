package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import ues.edu.sv.education.model.entity.VerificationToken;
import ues.edu.sv.education.repository.VerificationTokenRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-02 criterio 3 (el enlace de confirmacion vale 24 horas) y el flujo de
 * alta por el administrador: una cuenta creada desde /usuarios tiene que poder
 * llegar a entrar, con el enlace o, si el correo falla, con el reenvio.
 */
class ConfirmacionDeCuentaIT extends PruebaClinica {

    @Autowired private VerificationTokenRepository tokens;

    private List<VerificationToken> tokensDe(int userId) {
        return tokens.findAll().stream()
                .filter(t -> t.getUser().getUserID() == userId)
                .toList();
    }

    private int intentarEntrar(String correo) throws Exception {
        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(correo, CLAVE)))
                .andReturn().getResponse().getStatus();
    }

    private int altaPorAdmin(String admin, String correo) throws Exception {
        String cuerpo = mockMvc.perform(post("/user")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","userName":"Medico Nuevo","password":"%s"}
                                """.formatted(correo, CLAVE)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode r = json.readTree(cuerpo);
        assertTrue(r.get("correoDeVerificacionEnviado").asBoolean());
        return r.get("userId").asInt();
    }

    @Test
    @DisplayName("el enlace de confirmacion de un alta por el administrador vence a las 24 horas")
    void elEnlaceVenceA24Horas() throws Exception {
        String admin = tokenDeAdministrador();
        String correo = correoUnico("confirmacion.alta");
        int userId = altaPorAdmin(admin, correo);

        LocalDateTime vence = tokensDe(userId).get(0).getExpiresAt();
        LocalDateTime ahora = LocalDateTime.now();
        assertTrue(vence.isAfter(ahora.plusHours(23).plusMinutes(50)), "vence en " + vence);
        assertTrue(vence.isBefore(ahora.plusHours(24).plusMinutes(5)), "vence en " + vence);
    }

    @Test
    @DisplayName("la cuenta creada por el administrador entra despues de confirmar con el enlace")
    void confirmarPermiteEntrar() throws Exception {
        String admin = tokenDeAdministrador();
        String correo = correoUnico("confirmacion.entra");
        int userId = altaPorAdmin(admin, correo);

        assertTrue(intentarEntrar(correo) >= 400, "sin confirmar no debe entrar");

        mockMvc.perform(get("/auth/confirm").param("token", tokensDe(userId).get(0).getToken()))
                .andExpect(status().is3xxRedirection());

        assertEquals(200, intentarEntrar(correo));
    }

    @Test
    @DisplayName("reenviar la confirmacion emite un enlace nuevo, invalida el anterior y permite entrar")
    void reenviarEmiteUnEnlaceNuevo() throws Exception {
        String admin = tokenDeAdministrador();
        String correo = correoUnico("confirmacion.reenvio");
        int userId = altaPorAdmin(admin, correo);
        String viejo = tokensDe(userId).get(0).getToken();

        String cuerpo = mockMvc.perform(post("/user/{id}/confirmacion", userId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(json.readTree(cuerpo).get("correoDeVerificacionEnviado").asBoolean());

        List<VerificationToken> vigentes = tokensDe(userId);
        assertEquals(1, vigentes.size(), "un solo enlace vigente por cuenta");
        assertFalse(viejo.equals(vigentes.get(0).getToken()));

        mockMvc.perform(get("/auth/confirm").param("token", vigentes.get(0).getToken()))
                .andExpect(status().is3xxRedirection());
        assertEquals(200, intentarEntrar(correo));
    }

    @Test
    @DisplayName("no se reenvia a una cuenta ya confirmada, ni a una inexistente, ni lo hace un medico")
    void reenvioRechazado() throws Exception {
        String admin = tokenDeAdministrador();
        String correo = correoUnico("confirmacion.rechazo");
        int userId = altaPorAdmin(admin, correo);
        mockMvc.perform(get("/auth/confirm").param("token", tokensDe(userId).get(0).getToken()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/user/{id}/confirmacion", userId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/user/{id}/confirmacion", 99_999_999)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/user/{id}/confirmacion", userId)
                        .header("Authorization", "Bearer " + tokenDeMedico()))
                .andExpect(status().isForbidden());
    }
}
