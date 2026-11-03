package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.internet.MimeMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import ues.edu.sv.education.model.entity.User;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Que pasa con el alta de una cuenta (POST /user, la hace el administrador)
 * cuando el correo de confirmacion NO se puede enviar.
 *
 * Nacio para /auth/register, el registro publico que se retiro (HU-02). La
 * regla sigue valiendo para el alta que quedo.
 *
 * Esta prueba es la que faltaba. Sin ella el defecto llego hasta el final:
 * el envio del correo iba dentro del alta y sin proteccion, asi que cualquier
 * tropiezo de Gmail devolvia 500 y nadie podia registrarse. Nunca se noto en
 * la suite porque TODAS las pruebas corrian con un Gmail que funcionaba; el
 * dia que dejo de funcionar se cayeron a la vez las pruebas y el registro de
 * produccion.
 *
 * La regla que se fija aqui: la cuenta es el dato y el correo es solo una
 * notificacion. Un fallo de la notificacion no puede destruir el dato.
 */
class RegistroSinCorreoIT extends PruebaClinica {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationContext contexto;

    // El alta COMMITEA, asi que cada prueba necesita su propio correo o la
    // siguiente chocaria con "el correo ya esta registrado".
    private static final AtomicInteger CONTADOR = new AtomicInteger();

    private static final String NOMBRE = "Medico Sin Correo";

    private String registrarYDevolverRespuesta(String correo) throws Exception {
        String admin = tokenDeAdministrador();
        // El inicio de sesion del administrador no manda correo (el procesador
        // de eventos no corre en pruebas), pero se limpia por si acaso: aqui
        // solo interesa el de la cuenta nueva.
        clearInvocations(correoDePruebas);
        return mockMvc.perform(post("/user")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","userName":"%s","password":"%s"}
                                """.formatted(correo, NOMBRE, CLAVE)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("si el envio del correo falla, el registro sigue creando la cuenta")
    void siElCorreoFallaLaCuentaSeCreaIgual() throws Exception {
        // El servidor de correo esta caido, como lo estuvo Gmail.
        String correo = "sin.correo." + CONTADOR.incrementAndGet() + "@ues.edu.sv";
        String admin = tokenDeAdministrador();
        doThrow(new MailSendException("SMTP caido a proposito para esta prueba"))
                .when(correoDePruebas).send(any(MimeMessage.class));

        // 1. La API responde exito, no 500.
        String cuerpo = mockMvc.perform(post("/user")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","userName":"%s","password":"%s"}
                                """.formatted(correo, NOMBRE, CLAVE)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode respuesta = json.readTree(cuerpo);

        // 2. Y lo dice sin mentir: el correo NO salio. El cliente necesita este
        //    dato para no mandar a la persona a revisar una bandeja vacia.
        assertFalse(respuesta.get("correoDeVerificacionEnviado").asBoolean(),
                "la respuesta no debe afirmar que el correo salio cuando el envio fallo");

        // 3. El usuario existe en base, deshabilitado, tal como en un registro normal.
        User usuario = usuarios.findByEmailIgnoreCase(correo)
                .orElseThrow(() -> new AssertionError(
                        "el fallo del correo se llevo por delante la creacion de la cuenta"));
        assertFalse(usuario.isEnabled(),
                "la cuenta debe nacer deshabilitada, a la espera de la confirmacion");
        assertNotNull(respuesta.get("userId"), "la respuesta debe traer el id del usuario creado");

        // 4. Y su token de verificacion quedo guardado y vigente: la cuenta se
        //    puede confirmar en cuanto el correo se reenvie.
        Integer tokensVigentes = jdbc.queryForObject(
                "select count(*) from verification_token "
                        + "where user_id = ? and used = false and expires_at > now()",
                Integer.class, usuario.getUserID());
        assertEquals(1, tokensVigentes,
                "la cuenta debe quedar con exactamente un token de verificacion valido");
    }

    @Test
    @DisplayName("con el correo funcionando, el registro lo reporta y manda el enlace de confirmacion")
    void conCorreoDisponibleElRegistroLoReporta() throws Exception {
        String correo = "con.correo." + CONTADOR.incrementAndGet() + "@ues.edu.sv";

        JsonNode respuesta = json.readTree(registrarYDevolverRespuesta(correo));
        assertTrue(respuesta.get("correoDeVerificacionEnviado").asBoolean(),
                "con el emisor disponible la respuesta debe reportar el correo como enviado");

        // Se revisa el mensaje entregado al emisor. Ahora es exactamente uno: el
        // trabajo programado de EventProcessorService ya no corre en pruebas
        // (ver PruebaDeIntegracion), asi que no hay correos ajenos de por medio.
        ArgumentCaptor<MimeMessage> mensajes = ArgumentCaptor.forClass(MimeMessage.class);
        verify(correoDePruebas, times(1)).send(mensajes.capture());

        MimeMessage confirmacion = mensajes.getValue();
        assertEquals(correo, CorreosDePrueba.destinatario(confirmacion),
                "el correo de confirmacion debe ir a quien se registro");

        // El detalle de lo que va dentro esta en CorreoDeConfirmacionIT; aqui
        // basta con que el enlace este, que es lo que hace util al correo.
        String texto = CorreosDePrueba.parteDeTexto(confirmacion);
        assertNotNull(texto, "el correo de confirmacion no puede ir vacio");
        assertTrue(texto.contains("/auth/confirm?token="),
                "el correo debe llevar el enlace de confirmacion: " + texto);
    }

    @Test
    @DisplayName("las pruebas no hablan con ningun servidor SMTP real")
    void enPruebasNoHayEmisorDeCorreoReal() {
        JavaMailSender emisor = contexto.getBean(JavaMailSender.class);

        // Guardia para el futuro: si alguien quita la simulacion de
        // PruebaDeIntegracion, la suite vuelve a mandar correos de verdad con la
        // cuenta compartida del proyecto. Esta prueba lo caza en ese mismo commit
        // en vez de descubrirlo cuando Gmail bloquee la cuenta otra vez.
        assertTrue(Mockito.mockingDetails(emisor).isMock(),
                "el JavaMailSender del contexto de pruebas debe ser simulado");
        assertFalse(emisor instanceof JavaMailSenderImpl,
                "el contexto de pruebas no debe tener el emisor SMTP real");
    }
}
