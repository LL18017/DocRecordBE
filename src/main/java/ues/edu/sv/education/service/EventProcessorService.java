package ues.edu.sv.education.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.entity.*;
import ues.edu.sv.education.model.enums.EventCodeEnums;
import ues.edu.sv.education.model.enums.EventStatusEnums;
import ues.edu.sv.education.repository.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Trabajo programado que manda los avisos de inicio de sesion.
 *
 * El login no manda el correo: guarda un evento PENDING y devuelve la respuesta.
 * Este servicio los recoge cada 15 segundos y los notifica. Asi el tiempo de
 * respuesta del login no depende de lo que tarde Gmail.
 *
 * ── Como se tratan los fallos, y por que ──────────────────────────────────
 * Corre cada 15 segundos, sin nadie mirando. Eso cambia las reglas: un error
 * que aqui se silencie no se descubre nunca y se repite 5.760 veces al dia.
 * Antes habia un catch(Exception) con printStackTrace() dentro, que es la peor
 * combinacion posible: se tragaba tambien los fallos de programacion, y los
 * escribia a stderr sin fecha, sin nivel y fuera del sistema de logs, o sea
 * donde nadie los busca.
 *
 * Ahora se distinguen dos cosas que no son iguales:
 *
 *   MailException     fallo ESPERADO de un servicio externo (SMTP caido, cuota
 *                     agotada, credenciales rotadas). El evento queda FAILED y
 *                     se registra con log.error y la excepcion completa, porque
 *                     el stack trace trae la respuesta del servidor SMTP.
 *
 *   RuntimeException  cualquier otra cosa es un DEFECTO nuestro. Tambien se
 *                     registra -con la etiqueta de defecto, para que no se
 *                     confunda con una caida de Gmail- y el evento queda FAILED.
 *
 * ── Por que FAILED y no reintentar ────────────────────────────────────────
 * Dejar el evento en PENDING seria reintentarlo cada 15 segundos para siempre.
 * Con el SMTP caido eso son miles de intentos identicos llenando el log y
 * tapando cualquier otro error; con un defecto de codigo, la misma excepcion
 * repetida hasta que alguien la corrija. Y lo que se pierde al no reintentar es
 * poco: es un aviso de seguridad de un login concreto, que ya paso; el
 * siguiente login genera su propio evento. La tabla events guarda el FAILED con
 * su fecha, asi que lo ocurrido queda registrado y se puede consultar.
 *
 * ── Por que una transaccion por evento ────────────────────────────────────
 * El metodo entero estaba anotado @Transactional, con lo cual los correos se
 * mandaban dentro de una unica transaccion que solo confirmaba al terminar la
 * tanda. Si algo reventaba en el evento numero 20, los 19 correos anteriores YA
 * habian salido pero su marca de PROCESSED se iba con el rollback: volvian a
 * PENDING y 15 segundos despues se enviaban otra vez. Duplicados infinitos.
 * Ahora cada evento se confirma solo, en cuanto se envia el suyo.
 */
@Slf4j
@Service
public class EventProcessorService {

    private static final DateTimeFormatter FECHA_LEGIBLE =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es-SV"));

    private final EventRepository eventRepository;
    private final EventStatusRepository eventStatusRepository;
    private final EmailService emailService;
    private final PrescripcionRepository prescripcionRepository;
    private final ConsultaRepository consultaRepository;
    private final ReportService reportService;
    private final CitaRepository citaRepository;

    /**
     * Se construye a mano en vez de inyectar TransactionTemplate para no
     * depender de que la autoconfiguracion de Spring Boot lo exponga como bean.
     */
    private final TransactionTemplate transacciones;

    public EventProcessorService(EventRepository eventRepository,
                                 EventStatusRepository eventStatusRepository,
                                 EmailService emailService, PrescripcionRepository prescripcionRepository, ConsultaRepository consultaRepository, ReportService reportService, CitaRepository citaRepository,
                                 PlatformTransactionManager gestorDeTransacciones) {
        this.eventRepository = eventRepository;
        this.eventStatusRepository = eventStatusRepository;
        this.emailService = emailService;
        this.prescripcionRepository = prescripcionRepository;
        this.consultaRepository = consultaRepository;
        this.reportService = reportService;
        this.citaRepository = citaRepository;
        this.transacciones = new TransactionTemplate(gestorDeTransacciones);
    }

    /**
     * El intervalo y el retraso inicial son configurables a proposito: las
     * pruebas ponen un retraso inicial enorme para que el trabajo NO arranque
     * solo. Si corriera de fondo, se llevaria los eventos que la prueba acaba de
     * crear y el resultado dependeria de quien llegue primero.
     */
    @Scheduled(fixedDelayString = "${app.eventos.intervalo-ms:15000}",
               initialDelayString = "${app.eventos.retraso-inicial-ms:0}")
    public void processLoginEvents() {

        // Solo los identificadores: las entidades se releen dentro de la
        // transaccion de cada evento, que es donde se van a modificar.
        List<Integer> pendientes = eventRepository
                .getEventByType(EventCodeEnums.LOGIN.getId(), EventStatusEnums.PENDING.getId())
                .stream()
                .map(Event::getEventId)
                .toList();

        if (pendientes.isEmpty()) {
            // A nivel debug y no con System.out.println: esto corre cada 15
            // segundos y la inmensa mayoria de las veces no hay nada que hacer.
            // Escribirlo siempre a la consola solo sirve para enterrar los
            // mensajes que si importan.
            log.debug("Sin eventos de inicio de sesion pendientes");
            return;
        }

        log.debug("Procesando {} evento(s) de inicio de sesion", pendientes.size());

        for (Integer eventoId : pendientes) {
            try {
                transacciones.executeWithoutResult(estado -> notificarInicioDeSesion(eventoId));

            } catch (MailException ex) {
                marcarComoFallido(eventoId);
                log.error("No se pudo enviar el aviso de inicio de sesion del evento {}. Queda marcado "
                        + "como FAILED y no se reintenta; el proximo inicio de sesion genera su propio "
                        + "aviso.", eventoId, ex);

            } catch (RuntimeException ex) {
                // Un evento envenenado no puede detener la cola ni repetirse
                // cada 15 segundos, pero tampoco puede pasar desapercibido: va
                // a error y con la palabra DEFECTO, porque esto se corrige en el
                // codigo, no esperando a que Gmail se recupere.
                marcarComoFallido(eventoId);
                log.error("DEFECTO al procesar el evento {}: fallo algo que no es el envio de correo. "
                        + "Queda marcado como FAILED; hay que corregir el codigo.", eventoId, ex);
            }
        }
    }

    /**
     * El intervalo y el retraso inicial son configurables a proposito: las
     * pruebas ponen un retraso inicial enorme para que el trabajo NO arranque
     * solo. Si corriera de fondo, se llevaria los eventos que la prueba acaba de
     * crear y el resultado dependeria de quien llegue primero.
     */
    @Scheduled(fixedDelayString = "${app.eventos.intervalo-ms:15000}",
            initialDelayString = "${app.eventos.retraso-inicial-ms:0}")
    public void processPreescripcionEvents() {

        // Solo los identificadores: las entidades se releen dentro de la
        // transaccion de cada evento, que es donde se van a modificar.
        List<Integer> pendientes = eventRepository
                .getEventByType(EventCodeEnums.PREESCRIPTION_SEND.getId(), EventStatusEnums.PENDING.getId())
                .stream()
                .map(Event::getEventId)
                .toList();

        if (pendientes.isEmpty()) {
            // A nivel debug y no con System.out.println: esto corre cada 15
            // segundos y la inmensa mayoria de las veces no hay nada que hacer.
            // Escribirlo siempre a la consola solo sirve para enterrar los
            // mensajes que si importan.
            log.debug("Sin ENVIO DE PREESCRIPCIONES");
            return;
        }

        log.debug("Procesando {} evento(s) PREESCRIPCIONES", pendientes.size());

        for (Integer eventoId : pendientes) {
            try {
                transacciones.executeWithoutResult(estado -> notificarPreescripcion(eventoId));

            } catch (MailException ex) {
                log.error("No se pudo enviar el aviso DE PREESCRIPCIONES del evento {}. Queda marcado PENDIENTE"
                        + "aviso.", eventoId, ex);

            } catch (RuntimeException ex) {
                // Un evento envenenado no puede detener la cola ni repetirse
                // cada 15 segundos, pero tampoco puede pasar desapercibido: va
                // a error y con la palabra DEFECTO, porque esto se corrige en el
                // codigo, no esperando a que Gmail se recupere.
                marcarComoFallido(eventoId);
                log.error("DEFECTO al procesar el evento {}: fallo algo que no es el envio de correo. "
                        + "Queda marcado como FAILED; hay que corregir el codigo.", eventoId, ex);
            }
        }
    }

    @Scheduled(cron = "0 0 17 * * *", zone = "America/El_Salvador")
    public void recordarCitas() {
        ZoneId zona = ZoneId.of("America/El_Salvador");

        LocalDateTime ahora = LocalDateTime.now(zona);
        LocalDateTime limite = ahora.plusDays(1);

        List<Cita> citas = citaRepository.findByFechaHoraBetween(ahora, limite);

        log.info("Se encontraron {} citas próximas para recordar", citas.size());

        for (Cita cita : citas) {
            try {
                notificarCita(cita);
            } catch (MailException ex) {
                log.error(
                        "No se pudo enviar el recordatorio de la cita {}",
                        cita.getCitaId(),
                        ex
                );
            } catch (Exception ex) {
                log.error(
                        "Error al procesar el recordatorio de la cita {}",
                        cita.getCitaId(),
                        ex
                );
            }
        }
    }
    //citas durante el dia
    @Scheduled(cron = "0 0 5 * * *", zone = "America/El_Salvador")
    public void recordarCitasHoy() {
        ZoneId zona = ZoneId.of("America/El_Salvador");

        LocalDate hoy = LocalDate.now(zona);
        LocalDateTime inicio = hoy.atStartOfDay();
        LocalDateTime fin = hoy.plusDays(1).atStartOfDay();

        List<Cita> citas = citaRepository.findByFechaHoraBetween(
                inicio,
                fin
        );

        log.info("Se encontraron {} citas para hoy", citas.size());

        for (Cita cita : citas) {
            try {
                notificarCita(cita);
            } catch (MailException ex) {
                log.error("No se pudo enviar el recordatorio de la cita {}",
                        cita.getCitaId(), ex);
            } catch (Exception ex) {
                log.error("Error al procesar el recordatorio de la cita {}",
                        cita.getCitaId(), ex);
            }
        }
    }

    /**
     * El intervalo y el retraso inicial son configurables a proposito: las
     * pruebas ponen un retraso inicial enorme para que el trabajo NO arranque
     * solo. Si corriera de fondo, se llevaria los eventos que la prueba acaba de
     * crear y el resultado dependeria de quien llegue primero.
     */
    @Scheduled(fixedDelayString = "${app.eventos.intervalo-ms:15000}",
            initialDelayString = "${app.eventos.retraso-inicial-ms:0}")
    public void processAppoinmentEvents() {

        // Solo los identificadores: las entidades se releen dentro de la
        // transaccion de cada evento, que es donde se van a modificar.
        List<Event> pendientes = eventRepository
                .getEventByType(EventCodeEnums.APPOINTMENT_CREATED.getId(), EventStatusEnums.PENDING.getId())
                .stream()
                .toList();

        if (pendientes.isEmpty()) {
            // A nivel debug y no con System.out.println: esto corre cada 15
            // segundos y la inmensa mayoria de las veces no hay nada que hacer.
            // Escribirlo siempre a la consola solo sirve para enterrar los
            // mensajes que si importan.
            log.debug("Sin ENVIO DE CITAS");
            return;
        }

        log.debug("Procesando {} evento(s) CITAS", pendientes.size());

        for (Event evento : pendientes) {

            try {
                Cita cita=citaRepository.buscarCitaConPersonas(Integer.parseInt(evento.getRef())).orElseThrow(
                        ()->new NoResourceFoundException("NO SE ENCONTRO LA CITA "+evento.getRef(),"404")
                );
                transacciones.executeWithoutResult(estado -> notificarCita(cita));

                evento.setEventStatus(eventStatusRepository.getReferenceById(EventStatusEnums.PROCESSED.getId()));
                eventRepository.save(evento);

            } catch (MailException ex) {
                log.error("No se pudo enviar el aviso de cita del evento {}. Queda marcado PENDIENTE"
                        + "aviso.", evento.getEventId(), ex);

            } catch (RuntimeException ex) {
                // Un evento envenenado no puede detener la cola ni repetirse
                // cada 15 segundos, pero tampoco puede pasar desapercibido: va
                // a error y con la palabra DEFECTO, porque esto se corrige en el
                // codigo, no esperando a que Gmail se recupere.
                marcarComoFallido(evento.getEventId());
                log.error("DEFECTO al procesar el evento {}: fallo algo que no es el envio de correo. "
                        + "Queda marcado como FAILED; hay que corregir el codigo.", evento.getEventId(), ex);
            }
        }
    }

    /**
     * Manda el aviso de un evento concreto y lo deja como PROCESSED.
     *
     * Ya no se pasa por PROCESSING: al vivir todo dentro de una transaccion, ese
     * estado intermedio no lo veia nadie -se sobreescribia antes de confirmar- y
     * como marca de "lo estoy trabajando" tampoco hacia falta, porque @Scheduled
     * con fixedDelay nunca solapa dos ejecuciones.
     */
    private void notificarInicioDeSesion(Integer eventoId) {

        Event evento = eventRepository.findById(eventoId)
                .orElseThrow(() -> new IllegalStateException("El evento " + eventoId + " ya no existe"));

        Map<String, Object> datos = new HashMap<>();
        datos.put("correo", evento.getUserEmail());
        datos.put("fechaHora", FECHA_LEGIBLE.format(evento.getCreatedAt()));
        // El login SI la guarda ahora (ver AuthService.loging). Puede seguir
        // viniendo vacia en eventos creados antes de que se llenara, y la
        // plantilla omite el bloque en ese caso en vez de ensenar un hueco.
        datos.put("ip", evento.getIpAddress());

        emailService.enviarCorreo(evento.getUserEmail(), PlantillaDeCorreo.AVISO_DE_INICIO_DE_SESION, datos);

        evento.setEventStatus(eventStatusRepository.getReferenceById(EventStatusEnums.PROCESSED.getId()));
        evento.setProcessedAt(LocalDateTime.now());
        eventRepository.save(evento);
    }

    private void notificarPreescripcion(Integer eventoId) {

        Event evento = eventRepository.findById(eventoId)
                .orElseThrow(() -> new NoResourceFoundException("El evento " + eventoId + " ya no existe"));
        long consultaId;
        try {
            consultaId=Long.parseLong(evento.getRef());
        }catch (Exception E){
            throw new GeneralException("Error al obtener el id de referencia del evento "+evento.getEventId(),"409");
        }

        Consulta consulta = consultaRepository.findById(consultaId)
                .orElseThrow(() -> new NoResourceFoundException("la consulta " + consultaId + " ya no existe"));

        List<Prescripcion> preescripcion = prescripcionRepository.buscarPorConsulta(consultaId);
        List<PrescripcionMedicamento> med=preescripcion.stream().flatMap((p)->p.getMedicamentos().stream()).toList();
        Map<String, Object> datos = new HashMap<>();
        datos.put("correo", evento.getUserEmail());
        datos.put("fecha", FECHA_LEGIBLE.format(evento.getCreatedAt()));
        datos.put("nombrePaciente", consulta.getPaciente().getPersona().getNombres());
        datos.put("nombreMedico", consulta.getMedico().getPersona().getNombres());
        datos.put("medicamentos", med);
        byte[] pdf = reportService.generarPreescripcionPdf(
                consulta.getPaciente().getPersona().getNombres(),
                consulta.getMedico().getPersona().getNombres(),
                FECHA_LEGIBLE.format(evento.getCreatedAt()),
                med
        );

        emailService.enviarCorreo(evento.getUserEmail(), PlantillaDeCorreo.NUEVA_PRESCRIPCION, datos,pdf,"receta");

        evento.setEventStatus(eventStatusRepository.getReferenceById(EventStatusEnums.PROCESSED.getId()));
        evento.setProcessedAt(LocalDateTime.now());
        eventRepository.save(evento);
    }

    public void notificarCita(Cita cita){

        Map<String, Object> datos = new HashMap<>();
        datos.put("correo", cita.getPaciente().getPersona().getEmail());
        datos.put("fechaHora", FECHA_LEGIBLE.format(cita.getFechaHora()));
        datos.put("medico", cita.getMedico().getPersona().getNombres());

        emailService.enviarCorreo(cita.getPaciente().getPersona().getEmail(), PlantillaDeCorreo.RECORDATORIO_CITA, datos);


    }

    /**
     * Deja el evento en FAILED, en una transaccion NUEVA.
     *
     * Tiene que ser nueva: si lo que fallo fue la base de datos, la transaccion
     * del evento ya esta marcada para rollback y escribir en ella no guardaria
     * nada. Y si tampoco se puede marcar, se registra y se sigue con los demas;
     * quedarse a medias por esto seria cambiar un problema por otro peor.
     */
    private void marcarComoFallido(Integer eventoId) {
        try {
            transacciones.executeWithoutResult(estado -> eventRepository.findById(eventoId).ifPresent(evento -> {
                evento.setEventStatus(eventStatusRepository.getReferenceById(EventStatusEnums.FAILED.getId()));
                evento.setProcessedAt(LocalDateTime.now());
                eventRepository.save(evento);
            }));
        } catch (RuntimeException ex) {
            log.error("Tampoco se pudo marcar como FAILED el evento {}; seguira apareciendo como "
                    + "pendiente en la proxima pasada.", eventoId, ex);
        }
    }


}
