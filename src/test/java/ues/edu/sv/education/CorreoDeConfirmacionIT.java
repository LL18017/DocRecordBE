package ues.edu.sv.education;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Que va DENTRO del correo de confirmacion de registro.
 *
 * Es el correo del que depende todo: la cuenta nace deshabilitada y solo ese
 * enlace la activa. Que "se haya enviado un correo" no dice nada; lo que hay que
 * comprobar es que ese correo sirva para lo que existe.
 *
 * Lo que estas pruebas dejan clavado, y que se rompio o pudo romperse al pasar
 * de texto plano a HTML:
 *
 *   - que el enlace del HTML lleve EL token de esta cuenta, no cualquiera;
 *   - que ademas del boton vaya la direccion escrita, para el caso -real- de un
 *     cliente que bloquea estilos o imagenes;
 *   - que el correo siga llevando parte de texto plano, porque hay clientes que
 *     no ensenan HTML y esa persona tiene que poder activar su cuenta igual;
 *   - y que las dos partes no esten cambiadas de sitio. setText(texto, html)
 *     lleva los argumentos en un orden que es facil invertir, y al hacerlo el
 *     usuario ve el codigo HTML crudo. Compila y "manda el correo" igual.
 */
@AutoConfigureMockMvc
class CorreoDeConfirmacionIT extends PruebaDeIntegracion {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final String CLAVE = "Docrecord2026!";

    /** Registra una cuenta nueva y devuelve el correo que se le mando. */
    private MimeMessage registrarYCapturarElCorreo(String correo) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Ana","apellidos":"Melendez",
                                 "email":"%s","password":"%s","especialidadId":1}
                                """.formatted(correo, CLAVE)))
                .andExpect(status().isCreated());

        ArgumentCaptor<MimeMessage> mensajes = ArgumentCaptor.forClass(MimeMessage.class);
        // times(1): el trabajo programado no corre en pruebas (ver
        // PruebaDeIntegracion), asi que el unico correo posible es este.
        verify(correoDePruebas, times(1)).send(mensajes.capture());
        return mensajes.getValue();
    }

    private String tokenGuardadoDe(String correo) {
        List<String> tokens = jdbc.queryForList(
                "select t.token from verification_token t join users u on u.user_id = t.user_id "
                        + "where upper(u.email) = upper(?) and t.used = false",
                String.class, correo);
        assertEquals(1, tokens.size(), "la cuenta debe quedar con un solo token sin usar");
        return tokens.get(0);
    }

    @Test
    @DisplayName("el correo de confirmacion lleva en el HTML el enlace con el token de esa cuenta")
    void elHtmlLlevaElEnlaceDeConfirmacion() throws Exception {
        String correo = "confirmacion." + CONTADOR.incrementAndGet() + "@ues.edu.sv";

        MimeMessage mensaje = registrarYCapturarElCorreo(correo);
        String enlaceEsperado = "http://localhost/auth/confirm?token=" + tokenGuardadoDe(correo);

        assertEquals(correo, CorreosDePrueba.destinatario(mensaje), "el correo debe ir a quien se registro");
        assertNotNull(CorreosDePrueba.asunto(mensaje), "un correo sin asunto acaba en la carpeta de spam");

        String html = CorreosDePrueba.parteHtml(mensaje);
        assertNotNull(html, "el correo de confirmacion debe llevar parte HTML");

        // El enlace, como destino de un ancla. No basta con que la direccion
        // aparezca en algun lado del texto: tiene que ser pulsable.
        assertTrue(html.contains("href=\"" + enlaceEsperado + "\""),
                "el HTML debe llevar el enlace de confirmacion en un href.\n" + html);

        // Y ademas escrito a la vista, fuera del atributo, para quien tenga el
        // boton roto o los estilos bloqueados.
        String sinAtributos = html.replace("href=\"" + enlaceEsperado + "\"", "");
        assertTrue(sinAtributos.contains(enlaceEsperado),
                "la direccion tambien debe ir escrita a la vista, no solo dentro del boton");

        // Identidad: si el correo no se parece a DocRecord Sv, quien lo reciba
        // hace bien en desconfiar de un enlace que le pide activar una cuenta.
        assertTrue(html.contains("DocRecord"), "el correo debe identificarse como DocRecord Sv");
        assertTrue(html.contains("#1E3A5F"), "el correo debe usar el azul de la aplicacion");
    }

    @Test
    @DisplayName("el correo de confirmacion lleva parte de texto plano con el enlace legible")
    void tambienVaEnTextoPlano() throws Exception {
        String correo = "texto.plano." + CONTADOR.incrementAndGet() + "@ues.edu.sv";

        MimeMessage mensaje = registrarYCapturarElCorreo(correo);
        String enlaceEsperado = "http://localhost/auth/confirm?token=" + tokenGuardadoDe(correo);

        String texto = CorreosDePrueba.parteDeTexto(mensaje);
        assertNotNull(texto, "el correo debe llevar parte de texto plano; hay clientes que no ensenan HTML");

        assertTrue(texto.contains(enlaceEsperado),
                "en la version de texto el enlace debe ir escrito completo y copiable.\n" + texto);

        // Que sea texto DE VERDAD y no el HTML repetido: si alguien invierte los
        // argumentos de setText(texto, html), esta prueba lo caza.
        assertFalse(texto.contains("<table"),
                "la parte de texto plano no puede ser el HTML; los argumentos de setText estan invertidos");
        assertFalse(texto.contains("style=\""),
                "la parte de texto plano no puede llevar estilos");

        // Las dos versiones tienen que decir lo mismo sobre el plazo: si una
        // dice 5 minutos y la otra otra cosa, alguien va a llegar tarde.
        assertTrue(texto.contains("5 minutos"), "el texto debe decir en cuanto vence el enlace.\n" + texto);
        assertTrue(CorreosDePrueba.parteHtml(mensaje).contains("5 minutos"),
                "el HTML debe decir el mismo plazo que el texto");
    }
}
