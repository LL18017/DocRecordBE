package ues.edu.sv.education.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import ues.edu.sv.education.service.Cita.DetalleDeReprogramacion;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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

    private static final Locale ESPANOL_SV = Locale.forLanguageTag("es-SV");
    private static final DateTimeFormatter DIA_LEGIBLE =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ESPANOL_SV);
    private static final DateTimeFormatter HORA_LEGIBLE = DateTimeFormatter.ofPattern("HH:mm", ESPANOL_SV);

    /** La hora de la cita es la de la clinica; el contenedor corre en UTC. */
    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");

    /** HU-33: se avisa cuando faltan entre 23 y 25 horas, o sea unas 24. */
    private static final Duration RECORDATORIO_DESDE = Duration.ofHours(23);
    private static final Duration RECORDATORIO_HASTA = Duration.ofHours(25);

    /**
     * A donde lleva el enlace de los correos de cita: la agenda de la
     * aplicacion, desde donde se cancela o se reprograma con el personal.
     */
    @Value("${app.frontend.url:http://localhost:3000}")
    private String urlDelFrontend;

    private final EventRepository eventRepository;
    private final EventStatusRepository eventStatusRepository;
    private final EmailService emailService;
    private final PrescripcionRepository prescripcionRepository;
    private final ConsultaRepository consultaRepository;
    private final ReportService reportService;
    private final CitaRepository citaRepository;
    private final UserRepository userRepository;

    /**
     * Se construye a mano en vez de inyectar TransactionTemplate para no
     * depender de que la autoconfiguracion de Spring Boot lo exponga como bean.
     */
    private final TransactionTemplate transacciones;

    public EventProcessorService(EventRepository eventRepository,
                                 EventStatusRepository eventStatusRepository,
                                 EmailService emailService, PrescripcionRepository prescripcionRepository, ConsultaRepository consultaRepository, ReportService reportService, CitaRepository citaRepository,
                                 UserRepository userRepository,
                                 PlatformTransactionManager gestorDeTransacciones) {
        this.eventRepository = eventRepository;
        this.eventStatusRepository = eventStatusRepository;
        this.emailService = emailService;
        this.prescripcionRepository = prescripcionRepository;
        this.consultaRepository = consultaRepository;
        this.reportService = reportService;
        this.citaRepository = citaRepository;
        this.userRepository = userRepository;
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
                // HU-25 criterio 4: el fallo tiene que ser visible. Antes solo se
                // registraba y el evento se quedaba PENDING para siempre, o sea
                // que la pantalla habria dicho "pendiente" eternamente. FAILED
                // es lo que la consulta muestra como FALLIDO, junto al boton de
                // reenviar. No se reintenta solo: la persona decide.
                marcarComoFallido(eventoId);
                log.error("No se pudo enviar la receta del evento {}. Queda marcado como FAILED y no se "
                        + "reintenta solo; se puede reenviar a mano desde la receta.", eventoId, ex);

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
     * HU-33 · Recordatorio de cita, UNA sola vez, unas 24 horas antes.
     *
     * Reemplaza a los dos trabajos diarios de antes (17:00 para "las proximas
     * 24 horas" y 05:00 para "hoy"): mandaban dos recordatorios por cita,
     * incluso a las ya avisadas, y no sabian de la hora exacta. Ahora corre
     * cada hora y toma las AGENDADAS cuya hora cae entre ahora+23h y ahora+25h
     * y que aun no tienen marca recordatorio_enviado_en. La ventana mide dos
     * horas y el trabajo corre cada una: toda cita cae al menos en una pasada,
     * y la marca impide que caiga en dos.
     *
     * Cancelada o atendida no entra (criterio 3): la consulta pide AGENDADA.
     */
    @Scheduled(fixedDelayString = "${app.recordatorios.intervalo-ms:3600000}",
            initialDelayString = "${app.eventos.retraso-inicial-ms:0}")
    public void recordarCitas() {
        recordarCitas(LocalDateTime.now(ZONA));
    }

    /** Lo mismo con "ahora" explicito, para poder probarlo sin esperar horas. */
    public void recordarCitas(LocalDateTime ahora) {

        List<Cita> citas = citaRepository.pendientesDeRecordatorio(
                ahora.plus(RECORDATORIO_DESDE), ahora.plus(RECORDATORIO_HASTA));

        if (citas.isEmpty()) {
            log.debug("Sin citas por recordar");
            return;
        }
        log.info("Se encontraron {} citas para recordar", citas.size());

        for (Cita cita : citas) {
            String correo = cita.getPaciente().getPersona().getEmail();
            if (correo == null || correo.isBlank()) {
                // Sin correo no hay recordatorio posible y no se marca: si el
                // paciente da su correo antes de la cita, la proxima pasada lo avisa.
                log.debug("La cita {} no tiene correo del paciente; sin recordatorio", cita.getCitaId());
                continue;
            }

            // Primero se RECLAMA la marca y despues se manda: si se mandara
            // primero y fallara el guardado de la marca, la hora siguiente
            // repetiria el aviso. Si el envio falla se devuelve la marca para
            // reintentar mientras la cita siga en la ventana.
            Integer reclamada = transacciones.execute(
                    estado -> citaRepository.reclamarRecordatorio(cita.getCitaId(), ahora));
            if (reclamada == null || reclamada == 0) {
                continue;
            }

            try {
                emailService.enviarCorreo(correo, PlantillaDeCorreo.RECORDATORIO_CITA, datosDeCita(cita));
            } catch (RuntimeException ex) {
                transacciones.executeWithoutResult(
                        estado -> citaRepository.liberarRecordatorio(cita.getCitaId()));
                if (ex instanceof MailException) {
                    log.error("No se pudo enviar el recordatorio de la cita {}; se reintenta en la proxima "
                            + "pasada mientras siga dentro de la ventana", cita.getCitaId(), ex);
                } else {
                    log.error("DEFECTO al armar el recordatorio de la cita {}", cita.getCitaId(), ex);
                }
            }
        }
    }

    /**
     * Avisos de cita: confirmacion al agendar (HU-30), cancelacion y
     * reprogramacion (HU-32). Los registra CitaService como eventos PENDING y
     * este trabajo los manda, igual que el aviso de inicio de sesion.
     *
     * Una transaccion por evento y FAILED si no se pudo enviar, por las mismas
     * razones del aviso de sesion (ver la cabecera de la clase). Antes un fallo
     * de SMTP dejaba el evento PENDING y se reintentaba cada 15 segundos sin fin.
     *
     * El intervalo y el retraso inicial son configurables a proposito: las
     * pruebas ponen un retraso inicial enorme para que el trabajo NO arranque
     * solo.
     */
    @Scheduled(fixedDelayString = "${app.eventos.intervalo-ms:15000}",
            initialDelayString = "${app.eventos.retraso-inicial-ms:0}")
    public void processAppoinmentEvents() {

        // En orden cronologico: una cita agendada y reprogramada enseguida debe
        // avisar primero lo uno y luego lo otro.
        List<Event> pendientes = new ArrayList<>();
        for (EventCodeEnums tipo : List.of(EventCodeEnums.APPOINTMENT_CREATED,
                EventCodeEnums.APPOINTMENT_CANCELLED, EventCodeEnums.APPOINTMENT_RESCHEDULED)) {
            pendientes.addAll(eventRepository.getEventByType(tipo.getId(), EventStatusEnums.PENDING.getId()));
        }
        pendientes.sort(Comparator.comparing(Event::getCreatedAt).thenComparing(Event::getEventId));

        if (pendientes.isEmpty()) {
            log.debug("Sin ENVIO DE CITAS");
            return;
        }

        log.debug("Procesando {} evento(s) CITAS", pendientes.size());

        for (Integer eventoId : pendientes.stream().map(Event::getEventId).toList()) {
            try {
                transacciones.executeWithoutResult(estado -> notificarEventoDeCita(eventoId));

            } catch (MailException ex) {
                marcarComoFallido(eventoId);
                log.error("No se pudo enviar el aviso de cita del evento {}. Queda marcado como FAILED y "
                        + "no se reintenta.", eventoId, ex);

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

    /** Un aviso listo para salir: a quien, con que plantilla y con que datos. */
    private record Aviso(String correo, PlantillaDeCorreo plantilla, Map<String, Object> datos) {
    }

    private void notificarEventoDeCita(Integer eventoId) {

        Event evento = eventRepository.findById(eventoId)
                .orElseThrow(() -> new IllegalStateException("El evento " + eventoId + " ya no existe"));

        Cita cita = citaRepository.buscarCitaConPersonas(Integer.parseInt(evento.getRef())).orElseThrow(
                () -> new NoResourceFoundException("NO SE ENCONTRO LA CITA " + evento.getRef(), "404"));

        List<Aviso> avisos = new ArrayList<>();
        String correoPaciente = cita.getPaciente().getPersona().getEmail();
        String correoMedico = cita.getMedico().getEmail();
        Map<String, Object> datos = datosDeCita(cita);
        EventCodeEnums tipo = EventCodeEnums.fromId(evento.getEventType().getEventTypeId());

        switch (tipo) {
            case APPOINTMENT_CREATED -> {
                // Si la cita ya se cancelo antes de que saliera la confirmacion,
                // confirmarla seria mentirle al paciente: la cancelacion tiene su aviso.
                if (Cita.AGENDADA.equals(cita.getEstado())) {
                    avisos.add(new Aviso(correoPaciente, PlantillaDeCorreo.CONFIRMACION_DE_CITA, datos));
                }
            }
            case APPOINTMENT_CANCELLED -> {
                datos.put("motivo", cita.getMotivoCancelacion());
                avisos.add(new Aviso(correoPaciente, PlantillaDeCorreo.CITA_CANCELADA, con(datos, "rol", "PACIENTE")));
                avisos.add(new Aviso(correoMedico, PlantillaDeCorreo.CITA_CANCELADA, con(datos, "rol", "MEDICO")));
            }
            case APPOINTMENT_RESCHEDULED -> {
                DetalleDeReprogramacion antes = DetalleDeReprogramacion.leer(evento.getDescription())
                        .orElseThrow(() -> new IllegalStateException(
                                "El evento " + eventoId + " no trae los datos de la cita anterior"));
                datos.put("fechaAnterior", DIA_LEGIBLE.format(antes.fechaAnterior()));
                datos.put("horaAnterior", HORA_LEGIBLE.format(antes.fechaAnterior()));
                avisos.add(new Aviso(correoPaciente, PlantillaDeCorreo.CITA_REPROGRAMADA, con(datos, "rol", "PACIENTE")));
                avisos.add(new Aviso(correoMedico, PlantillaDeCorreo.CITA_REPROGRAMADA, con(datos, "rol", "MEDICO")));
                // Si cambio el medico, el anterior tambien tiene que enterarse de
                // que ya no lo espera nadie en esa hora.
                if (!cita.getMedico().getUserID().equals(antes.medicoAnteriorId())) {
                    userRepository.findById(antes.medicoAnteriorId()).ifPresent(anterior ->
                            avisos.add(new Aviso(anterior.getEmail(), PlantillaDeCorreo.CITA_REPROGRAMADA,
                                    con(datos, "rol", "MEDICO_ANTERIOR"))));
                }
            }
            default -> throw new IllegalStateException("El evento " + eventoId + " no es un aviso de cita");
        }

        enviarAvisos(avisos);

        evento.setEventStatus(eventStatusRepository.getReferenceById(EventStatusEnums.PROCESSED.getId()));
        evento.setProcessedAt(LocalDateTime.now());
        eventRepository.save(evento);
    }

    /**
     * Manda todos los avisos que tengan destinatario. Si uno falla, intenta los
     * demas antes de propagar el error: que el correo del paciente falle no
     * debe dejar al medico sin enterarse. Quien no tiene correo se omite.
     */
    private void enviarAvisos(List<Aviso> avisos) {
        MailException primerFallo = null;
        for (Aviso aviso : avisos) {
            if (aviso.correo() == null || aviso.correo().isBlank()) {
                continue;
            }
            try {
                emailService.enviarCorreo(aviso.correo(), aviso.plantilla(), aviso.datos());
            } catch (MailException ex) {
                if (primerFallo == null) {
                    primerFallo = ex;
                }
            }
        }
        if (primerFallo != null) {
            throw primerFallo;
        }
    }

    private static Map<String, Object> con(Map<String, Object> datos, String clave, Object valor) {
        Map<String, Object> copia = new HashMap<>(datos);
        copia.put(clave, valor);
        return copia;
    }

    /**
     * Los datos que muestran todos los correos de cita (HU-33 criterio 2):
     * fecha, hora, medico y la clinica con nombre y direccion. La clinica puede
     * faltar en las citas anteriores a que la cita guardara su sede; las
     * plantillas omiten ese bloque cuando es null.
     */
    private Map<String, Object> datosDeCita(Cita cita) {
        Persona medico = cita.getMedico().getPersona();
        Persona paciente = cita.getPaciente().getPersona();

        Map<String, Object> datos = new HashMap<>();
        datos.put("fecha", DIA_LEGIBLE.format(cita.getFechaHora()));
        datos.put("hora", HORA_LEGIBLE.format(cita.getFechaHora()));
        datos.put("medico", (medico.getNombres() + " " + medico.getApellidos()).trim());
        datos.put("paciente", (paciente.getNombres() + " " + paciente.getApellidos()).trim());
        datos.put("clinica", cita.getClinica() == null ? null : cita.getClinica().getName());
        datos.put("direccion", cita.getClinica() == null ? null : direccionDe(cita.getClinica()));
        datos.put("enlace", urlDelFrontend.replaceAll("/+$", "") + "/agenda");
        return datos;
    }

    private static String direccionDe(Clinicas clinica) {
        String direccion = java.util.stream.Stream
                .of(clinica.getDireccion(), clinica.getMunicipio(), clinica.getDepartamento())
                .filter(parte -> parte != null && !parte.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
        return direccion.isEmpty() ? null : direccion;
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
