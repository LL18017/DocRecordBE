package ues.edu.sv.education.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.model.entity.*;
import ues.edu.sv.education.model.enums.EventCodeEnums;
import ues.edu.sv.education.model.enums.EventStatusEnums;
import ues.edu.sv.education.repository.*;
import ues.edu.sv.education.service.user.UserService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EventProcessorService {

    private static final DateTimeFormatter FECHA_LEGIBLE =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es-SV"));

    private final EventRepository eventRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final EventTypeRepository eventTypeRepository;
    private final EventStatusRepository eventStatusRepository;

    // El intervalo y el retraso inicial pasan a ser configurables: las pruebas
    // ponen un retraso enorme para que el trabajo no arranque solo y no compita
    // con los correos que ellas mismas quieren revisar.
    @Scheduled(fixedDelayString = "${app.eventos.intervalo-ms:15000}",
               initialDelayString = "${app.eventos.retraso-inicial-ms:0}")
    @Transactional
    public void processLoginEvents() {

        System.out.println("Revisando eventos...");

        // buscar eventos pendientes

        List<Event> loginEvents = this.eventRepository.getEventByType(EventCodeEnums.LOGIN.getId(), EventStatusEnums.PENDING.getId());

        // procesarlos

        for (Event event : loginEvents) {

            try {
                event.setEventStatus(eventStatusRepository.getReferenceById(EventStatusEnums.PROCESSING.getId()));
                EventCodeEnums eventCode = EventCodeEnums.fromId(event.getEventType().getEventTypeId());
                switch (eventCode){
                    case EventCodeEnums.LOGIN:
                        // El cuerpo ya no sale de event_types.description: ese
                        // texto era una etiqueta de catalogo, no un aviso de
                        // seguridad. Ahora lo pone la plantilla.
                        Map<String, Object> datos = new HashMap<>();
                        datos.put("correo", event.getUserEmail());
                        datos.put("fechaHora", FECHA_LEGIBLE.format(event.getCreatedAt()));
                        datos.put("ip", event.getIpAddress());
                        emailService.enviarCorreo(event.getUserEmail(),
                                PlantillaDeCorreo.AVISO_DE_INICIO_DE_SESION, datos);
                        event.setEventStatus(eventStatusRepository.getReferenceById(EventStatusEnums.PROCESSED.getId()));
                        event.setProcessedAt(LocalDateTime.now());
                        eventRepository.save(event);
                        break;

                }

            } catch (Exception ex) {
                event.setEventStatus(eventStatusRepository.getReferenceById(EventStatusEnums.FAILED.getId()));
                eventRepository.save(event);

                ex.printStackTrace();
            }
        }
    }


    // marcarlos como procesados

}
