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
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EventProcessorService {

    private final EventRepository eventRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final EventTypeRepository eventTypeRepository;
    private final EventStatusRepository eventStatusRepository;
    @Scheduled(fixedDelay = 15000)
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
                        emailService.sendEmail(event.getUserEmail(),event.getEventType().getDescription(), event.getDescription());
                        event.setEventStatus(eventStatusRepository.getReferenceById(EventStatusEnums.PROCESSED.getId()));
                        event.setProcessedAt(LocalDateTime.now());
                        eventRepository.save(event);
                        break;
                        /*
                    case EventCodeEnums.CONFIRM_ACOUNT:
                        String id= event.getRef();
                        VerificationToken token = verificationTokenRepository.getReferenceById(Integer.valueOf(id));
                        if (token.getExpiresAt().isAfter(LocalDateTime.now())){
                            event.setEventStatus(eventStatusRepository.getReferenceById(EventStatusEnums.FAILED.getId()));
                            emailService.sendEmail(event.getUserEmail(),event.getEventType().getDescription(), "EL token para confirmar su cuenta ha expirado , " +
                                    "la informacion relacionda a esta ha sido eliminada ");
                            event.setProcessedAt(LocalDateTime.now());
                            eventRepository.save(event);
                            //Borrar token
                            verificationTokenRepository.delete(token);
                            //BORRA INFO DE USUARIO
                            userService.deleteUser(event.getUserEmail());
                        }

                        break;
                        */

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