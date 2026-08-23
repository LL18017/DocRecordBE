package ues.edu.sv.education;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import ues.edu.sv.education.model.entity.Event;
import ues.edu.sv.education.model.enums.EventCodeEnums;
import ues.edu.sv.education.model.enums.EventStatusEnums;
import ues.edu.sv.education.repository.EventRepository;
import ues.edu.sv.education.repository.EventStatusRepository;
import ues.edu.sv.education.repository.EventTypeRepository;
import ues.edu.sv.education.service.EventProcessorService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Que hace el trabajo programado cuando un evento falla.
 *
 * Importa mas de lo que parece porque nadie lo esta mirando: corre cada 15
 * segundos, sin usuario delante y sin peticion que devuelva un 500. Lo que aqui
 * se silencie se silencia para siempre, y ademas se repite 5.760 veces al dia.
 *
 * De ahi el defecto que estas pruebas cierran: el catch era catch(Exception) y
 * el registro del error era ex.printStackTrace(). Esa combinacion se tragaba
 * TODO -incluidos los fallos de programacion, que son los que hay que corregir-
 * y lo escupia a stderr, sin fecha, sin nivel, sin el id del evento y fuera del
 * sistema de logs. En el archivo de log del servidor no quedaba ni rastro.
 *
 * Por eso las comprobaciones miran el LOGGER y no la salida de la consola:
 * printStackTrace escribe en stderr sin generar ningun registro, asi que una
 * prueba que revisara la consola pasaria con el codigo viejo y con el nuevo. La
 * que mira el logger solo pasa con el nuevo.
 */
class ProcesadorDeEventosIT extends PruebaDeIntegracion {

    @Autowired private EventProcessorService procesador;
    @Autowired private EventRepository eventos;
    @Autowired private EventStatusRepository estados;
    @Autowired private EventTypeRepository tipos;
    @Autowired private JdbcTemplate jdbc;

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    private Logger loggerDelProcesador;
    private ListAppender<ILoggingEvent> registros;

    @BeforeEach
    void engancharElLogger() {
        registros = new ListAppender<>();
        registros.start();
        loggerDelProcesador = (Logger) LoggerFactory.getLogger(EventProcessorService.class);
        loggerDelProcesador.addAppender(registros);
    }

    @AfterEach
    void soltarElLogger() {
        loggerDelProcesador.detachAppender(registros);
        registros.stop();
    }

    private Event crearEventoDeLoginPendiente(String correo) {
        return eventos.save(Event.builder()
                .eventType(tipos.getReferenceById(EventCodeEnums.LOGIN.getId()))
                .eventStatus(estados.getReferenceById(EventStatusEnums.PENDING.getId()))
                .description("Se ha detectado un nuevo inicio de sesión")
                .userEmail(correo)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private int estadoDe(Integer eventoId) {
        Integer estado = jdbc.queryForObject(
                "select event_status_id from events where event_id = ?", Integer.class, eventoId);
        assertNotNull(estado);
        return estado;
    }

    private List<MimeMessage> correosEnviados() {
        ArgumentCaptor<MimeMessage> mensajes = ArgumentCaptor.forClass(MimeMessage.class);
        verify(correoDePruebas, atLeast(0)).send(mensajes.capture());
        return mensajes.getAllValues();
    }

    private long correosPara(String destinatario) {
        return correosEnviados().stream()
                .filter(m -> destinatario.equals(CorreosDePrueba.destinatario(m)))
                .count();
    }

    private List<ILoggingEvent> erroresRegistrados() {
        return registros.list.stream().filter(r -> r.getLevel() == Level.ERROR).toList();
    }

    @Test
    @DisplayName("si el SMTP falla, el evento queda FAILED, se registra con log.error y no se reintenta en bucle")
    void unFalloDeCorreoQuedaRegistradoYNoSeReintenta() {
        doThrow(new MailSendException("SMTP caido a proposito para esta prueba"))
                .when(correoDePruebas).send(any(MimeMessage.class));

        String correo = "evento.fallido." + CONTADOR.incrementAndGet() + "@ues.edu.sv";
        Event evento = crearEventoDeLoginPendiente(correo);

        procesador.processLoginEvents();

        // 1. El evento no se queda en PENDING. Si se quedara, la proxima pasada
        //    -dentro de 15 segundos- lo intentaria otra vez, y la siguiente, y
        //    la siguiente: un bucle que no termina nunca.
        assertEquals(EventStatusEnums.FAILED.getId(), estadoDe(evento.getEventId()),
                "un evento que no se pudo notificar debe quedar marcado como FAILED");

        // 2. Y el bucle se comprueba de verdad: una segunda pasada ya no lo toca.
        procesador.processLoginEvents();
        assertEquals(1, correosPara(correo),
                "el evento fallido no debe volver a intentarse en la siguiente pasada");

        // 3. El fallo dejo rastro EN EL LOG, con el id del evento y la excepcion
        //    completa. Con printStackTrace no habria ningun registro y esta
        //    comprobacion quedaria en rojo, que es justo lo que se quiere.
        List<ILoggingEvent> errores = erroresRegistrados();
        assertTrue(errores.stream().anyMatch(r ->
                        r.getFormattedMessage().contains(String.valueOf(evento.getEventId()))
                                && r.getThrowableProxy() != null),
                "el fallo debe registrarse con log.error, nombrando el evento y con la excepcion adjunta. "
                        + "Registrado: " + errores.stream().map(ILoggingEvent::getFormattedMessage).toList());
    }

    @Test
    @DisplayName("un fallo que no es de correo se registra como defecto y no bloquea el resto de la cola")
    void unDefectoNoDetieneLaColaNiPasaDesapercibido() {
        String correoRoto = "evento.roto." + CONTADOR.incrementAndGet() + "@ues.edu.sv";
        String correoSano = "evento.sano." + CONTADOR.incrementAndGet() + "@ues.edu.sv";

        // Falla solo uno, y con algo que NO es MailException: un error de
        // programacion cualquiera, de los que el catch(Exception) de antes se
        // tragaba sin dejar rastro.
        doAnswer(invocacion -> {
            MimeMessage mensaje = invocacion.getArgument(0);
            if (correoRoto.equals(CorreosDePrueba.destinatario(mensaje))) {
                throw new IllegalStateException("defecto simulado, no es un fallo de SMTP");
            }
            return null;
        }).when(correoDePruebas).send(any(MimeMessage.class));

        Event roto = crearEventoDeLoginPendiente(correoRoto);
        Event sano = crearEventoDeLoginPendiente(correoSano);

        procesador.processLoginEvents();

        // 1. El evento envenenado no arrastra a los demas: el siguiente de la
        //    cola se notifica igual.
        assertEquals(1, correosPara(correoSano),
                "un evento roto no puede impedir que se notifiquen los que vienen detras");
        assertEquals(EventStatusEnums.PROCESSED.getId(), estadoDe(sano.getEventId()),
                "el evento sano debe quedar como PROCESSED");

        // 2. Y su marca de PROCESSED tiene que sobrevivir. Cuando el metodo
        //    entero era una sola transaccion, el fallo de un evento posterior la
        //    borraba y el correo salia otra vez en la siguiente pasada.
        procesador.processLoginEvents();
        assertEquals(1, correosPara(correoSano),
                "el evento ya notificado no puede volver a enviarse: seria un correo duplicado");

        // 3. El defecto queda marcado y, sobre todo, registrado como defecto:
        //    no es lo mismo "Gmail esta caido" que "hay un error en el codigo",
        //    y el log tiene que poder distinguirlos.
        assertEquals(EventStatusEnums.FAILED.getId(), estadoDe(roto.getEventId()),
                "el evento que reventó debe quedar como FAILED, no reintentandose para siempre");
        assertTrue(erroresRegistrados().stream().anyMatch(r ->
                        r.getFormattedMessage().contains("DEFECTO")
                                && r.getFormattedMessage().contains(String.valueOf(roto.getEventId()))),
                "un fallo que no es de correo debe registrarse como defecto del codigo, no como caida del SMTP");
    }

    @Test
    @DisplayName("el aviso de inicio de sesion dice que hacer si la persona no reconoce el acceso")
    void elAvisoDeSesionExplicaQueHacer() {
        String correo = "aviso.sesion." + CONTADOR.incrementAndGet() + "@ues.edu.sv";
        crearEventoDeLoginPendiente(correo);

        procesador.processLoginEvents();

        MimeMessage aviso = correosEnviados().stream()
                .filter(m -> correo.equals(CorreosDePrueba.destinatario(m)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no se envio el aviso de inicio de sesion"));

        String html = CorreosDePrueba.parteHtml(aviso);
        String texto = CorreosDePrueba.parteDeTexto(aviso);
        assertNotNull(html, "el aviso debe llevar parte HTML");
        assertNotNull(texto, "el aviso debe llevar parte de texto plano");

        // Una alerta de seguridad que no dice que hacer no sirve de nada. Antes
        // el cuerpo entero era "Se ha detectado un nuevo inicio de sesión"; esta
        // comprobacion impide volver a eso.
        assertTrue(html.contains("cambia tu contraseña"),
                "el aviso debe decir que hacer si el acceso no se reconoce.\n" + html);
        assertTrue(texto.contains("cambia tu contraseña"),
                "la version de texto tambien debe decir que hacer.\n" + texto);
        assertTrue(html.contains(correo), "el aviso debe decir de que cuenta se trata");
    }
}
