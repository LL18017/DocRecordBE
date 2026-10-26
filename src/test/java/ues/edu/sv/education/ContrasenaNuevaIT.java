package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-03, criterio 2: la contrasena nueva exige minimo 8 caracteres, una
 * mayuscula y un numero.
 *
 * Se prueba por cada puerta que recibe una contrasena nueva, no solo por el
 * restablecimiento: la regla vive en un solo sitio (ReglaDeContrasena) justamente
 * para que ninguna se quede con la anterior, y esta clase lo vigila.
 */
class ContrasenaNuevaIT extends PruebaClinica {

    /** Cada una incumple una sola parte de la regla. */
    private static final String SIN_MAYUSCULA = "sinmayuscula1";
    private static final String SIN_NUMERO = "SinNumeroAqui";
    private static final String MUY_CORTA = "Ab1cdef";

    private String cuerpoDeRestablecer(String clave) {
        return """
                {"token":"un-token-cualquiera","password":"%s"}
                """.formatted(clave);
    }

    @ParameterizedTest(name = "restablecer rechaza \"{0}\"")
    @ValueSource(strings = {SIN_MAYUSCULA, SIN_NUMERO, MUY_CORTA})
    @DisplayName("restablecer rechaza una contrasena que incumple la regla, con un mensaje que la explica")
    void restablecerRechazaContrasenasDebiles(String clave) throws Exception {
        // 400 por la validacion del cuerpo, antes de que el token se mire: si
        // la regla no se aplicara, la respuesta seria el 400 del token
        // invalido y el mensaje no hablaria de la contrasena.
        String cuerpo = mockMvc.perform(post("/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeRestablecer(clave)))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        JsonNode respuesta = json.readTree(cuerpo);
        String mensaje = respuesta.get("message").asText();
        assertTrue(mensaje.contains("8 caracteres") && mensaje.contains("mayuscula") && mensaje.contains("numero"),
                "el mensaje debe decir la regla completa: " + mensaje);
    }

    @Test
    @DisplayName("una contrasena que cumple la regla pasa la validacion (falla despues, por el token)")
    void restablecerAceptaUnaContrasenaValida() throws Exception {
        String cuerpo = mockMvc.perform(post("/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeRestablecer("Valida2026x")))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        String mensaje = json.readTree(cuerpo).get("message").asText();
        assertTrue(!mensaje.contains("mayuscula"),
                "el rechazo debe ser el del token, no el de la contrasena: " + mensaje);
    }

    @ParameterizedTest(name = "el registro rechaza \"{0}\"")
    @ValueSource(strings = {SIN_MAYUSCULA, SIN_NUMERO, MUY_CORTA})
    @DisplayName("el registro publico aplica la misma regla")
    void elRegistroRechazaContrasenasDebiles(String clave) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Medico","apellidos":"Debil",
                                 "email":"%s","password":"%s","especialidadId":1}
                                """.formatted(correoUnico("clave.debil"), clave)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "el administrador no puede asignar \"{0}\"")
    @ValueSource(strings = {SIN_MAYUSCULA, SIN_NUMERO, MUY_CORTA})
    @DisplayName("la asignacion de contrasena por un administrador aplica la misma regla")
    void laAsignacionRechazaContrasenasDebiles(String clave) throws Exception {
        String admin = tokenDeAdministrador();

        mockMvc.perform(post("/user/{id}/password", 1)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"%s\"}".formatted(clave)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "el alta de usuario rechaza \"{0}\"")
    @ValueSource(strings = {SIN_MAYUSCULA, SIN_NUMERO, MUY_CORTA})
    @DisplayName("el alta directa de un usuario aplica la misma regla")
    void elAltaRechazaContrasenasDebiles(String clave) throws Exception {
        String admin = tokenDeAdministrador();

        mockMvc.perform(post("/user")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","userName":"Usuario Debil","password":"%s"}
                                """.formatted(correoUnico("alta.debil"), clave)))
                .andExpect(status().isBadRequest());
    }
}
