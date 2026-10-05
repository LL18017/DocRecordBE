package ues.edu.sv.education;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El enlace del correo de confirmacion en produccion.
 *
 * Alli la API se publica bajo /api del mismo dominio que la aplicacion, y
 * Caddy quita el /api antes de pasar la peticion. Armado con la direccion de
 * la peticion, el enlace salia sin /api y abria una pantalla 404 del frontend:
 * nadie podia confirmar su cuenta. Con app.api.url se arma sobre la direccion
 * publica de la API, que es la que el navegador puede abrir.
 */
@TestPropertySource(properties = "app.api.url=https://docrecord.ejemplo/api")
class EnlaceDeConfirmacionIT extends PruebaClinica {

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final String ESPERADO = "https://docrecord.ejemplo/api/auth/confirm?token=";

    private String enlaceDelUltimoCorreo() throws Exception {
        ArgumentCaptor<MimeMessage> mensajes = ArgumentCaptor.forClass(MimeMessage.class);
        verify(correoDePruebas, atLeastOnce()).send(mensajes.capture());
        String texto = CorreosDePrueba.parteDeTexto(mensajes.getValue());
        return texto == null ? "" : texto;
    }

    @Test
    @DisplayName("registro publico: el enlace apunta a la API bajo /api, no a una ruta del frontend")
    void elRegistroPublicoUsaLaDireccionPublicaDeLaApi() throws Exception {
        clearInvocations(correoDePruebas);
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Ana","apellidos":"Melendez",
                                 "email":"enlace.publico.%d@ues.edu.sv","password":"%s","especialidadId":1}
                                """.formatted(CONTADOR.incrementAndGet(), CLAVE)))
                .andExpect(status().isCreated());

        String texto = enlaceDelUltimoCorreo();
        assertTrue(texto.contains(ESPERADO), "el enlace debe ir sobre app.api.url.\n" + texto);
        assertTrue(!texto.contains("http://localhost/auth/confirm"),
                "no debe quedar el enlace armado con la direccion de la peticion.\n" + texto);
    }

    @Test
    @DisplayName("alta hecha por un administrador: el mismo enlace, sobre la direccion publica de la API")
    void elAltaDelAdministradorUsaLaDireccionPublicaDeLaApi() throws Exception {
        String admin = tokenDeAdministrador();
        clearInvocations(correoDePruebas);
        mockMvc.perform(post("/user")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"enlace.alta.%d@ues.edu.sv","userName":"Personal De Turno","password":"%s"}
                                """.formatted(CONTADOR.incrementAndGet(), CLAVE)))
                .andExpect(status().isCreated());

        String texto = enlaceDelUltimoCorreo();
        assertTrue(texto.contains(ESPERADO), "el enlace debe ir sobre app.api.url.\n" + texto);
    }
}
