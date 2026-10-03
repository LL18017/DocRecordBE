package ues.edu.sv.education.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Unico punto por el que salen correos del sistema.
 *
 * ── Por que ya no es SimpleMailMessage ────────────────────────────────────
 * SimpleMailMessage solo manda texto plano, y el contenido se armaba
 * concatenando cadenas en el sitio donde se enviaba. Un sistema de expediente
 * clinico que pide "haz clic en este enlace para activar tu cuenta" desde un
 * correo sin remitente reconocible y sin ninguna sena de identidad se parece
 * demasiado a un intento de phishing; la reaccion sensata de quien lo recibe es
 * no hacer clic, que es justo lo contrario de lo que se necesita.
 *
 * ── Por que SIEMPRE las dos partes ────────────────────────────────────────
 * Todo correo sale como multipart/alternative: primero la version de texto,
 * despues la de HTML. El cliente ensena la ultima que entienda. Esto no es
 * cortesia: hay clientes y configuraciones corporativas que bloquean el HTML, y
 * en ese caso la persona TIENE que poder activar su cuenta igual. Por eso el
 * enlace aparece escrito completo tambien en la version de texto, y no
 * escondido detras de un boton.
 *
 * ── Que hace y que no hace con los errores ────────────────────────────────
 * Los fallos de envio salen como MailException, la jerarquia de Spring: quien
 * llama decide si el correo era imprescindible o no. Un fallo al ARMAR el
 * mensaje (una plantilla que no compila, una direccion mal formada) se envuelve
 * en MailPreparationException, que tambien es MailException, para que ese
 * contrato sea uno solo y nadie tenga que capturar ademas jakarta.mail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String CARPETA_HTML = "correo/html/";
    private static final String CARPETA_TEXTO = "correo/texto/";

    /** El nombre que se ve en la bandeja de entrada, junto a la direccion. */
    private static final String NOMBRE_DEL_REMITENTE = "DocRecord Sv";

    /** Textos en espanol de El Salvador (afecta numeros y fechas de las plantillas). */
    private static final Locale ESPANOL_SV = Locale.forLanguageTag("es-SV");

    private final JavaMailSender mailSender;
    private final ITemplateEngine motorDePlantillas;

    /**
     * Se remite desde la misma cuenta con la que se autentica el SMTP. Poner
     * otra cosa haria que Gmail reescribiera el From o marcara el correo como
     * sospechoso, que es de lo que se trata de huir.
     */
    @Value("${spring.mail.username:no-responder@docrecord.sv}")
    private String remitente;

    /**
     * Arma y manda uno de los correos del sistema.
     *
     * @param destinatario a quien va
     * @param plantilla    cual de los correos (trae su asunto y sus dos archivos)
     * @param datos        variables que la plantilla espera; ver el comentario
     *                     de cabecera de cada archivo .html
     * @throws org.springframework.mail.MailException si no se puede armar o enviar
     */
    public void enviarCorreo(String destinatario, PlantillaDeCorreo plantilla, Map<String, Object> datos) {

        Context contexto = new Context(ESPANOL_SV, datos);

        // Las dos versiones salen del MISMO contexto: no hay forma de que el
        // HTML diga una cosa y el texto otra.
        String cuerpoHtml = motorDePlantillas.process(CARPETA_HTML + plantilla.getNombreBase(), contexto);
        String cuerpoTexto = motorDePlantillas.process(CARPETA_TEXTO + plantilla.getNombreBase(), contexto);

        MimeMessage mensaje = mailSender.createMimeMessage();
        try {
            MimeMessageHelper ayudante = new MimeMessageHelper(
                    mensaje, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

            ayudante.setFrom(remitente, NOMBRE_DEL_REMITENTE);
            ayudante.setTo(destinatario);
            ayudante.setSubject(plantilla.getAsunto());
            // Este orden importa y no es intercambiable: setText(texto, html)
            // genera multipart/alternative con el texto como parte de respaldo.
            // Al reves, el cliente ensenaria el codigo HTML como si fuera texto.
            ayudante.setText(cuerpoTexto, cuerpoHtml);

        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new MailPreparationException(
                    "No se pudo armar el correo " + plantilla.name() + " para " + destinatario, ex);
        }

        mailSender.send(mensaje);
        log.debug("Correo {} enviado a {}", plantilla.name(), destinatario);
    }
}
