package ues.edu.sv.education.service.Cita;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.model.dto.cita.CitaRequestDto;
import ues.edu.sv.education.model.dto.cita.CitaResponseDto;
import ues.edu.sv.education.model.dto.cita.CitaUpdateRequestDto;
import ues.edu.sv.education.model.dto.cita.MedicoDeAgendaDto;
import ues.edu.sv.education.model.entity.Cita;
import ues.edu.sv.education.model.entity.Clinicas;
import ues.edu.sv.education.model.entity.Event;
import ues.edu.sv.education.model.entity.Paciente;
import ues.edu.sv.education.model.entity.TipoCita;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.enums.EventCodeEnums;
import ues.edu.sv.education.model.enums.EventStatusEnums;
import ues.edu.sv.education.model.enums.RolesEnum;
import ues.edu.sv.education.model.mappers.CitaMapper;
import ues.edu.sv.education.repository.*;
import ues.edu.sv.education.service.horario.HorarioDeMedicoService;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

/**
 * Citas (HU-30, HU-32).
 *
 * Los errores salen como GeneralException con su codigo HTTP: 400 para una
 * fecha mal formada o fuera de la grilla, 404 para lo que no existe, 409 para
 * los choques de agenda y las reglas de cancelacion. Antes salian como
 * RuntimeException y la API respondia 500 a cualquiera de ellos.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CitaService {

    private static final int DURACION_CITA_MINUTOS = 30;

    /** Una cita no se cancela ni se reprograma con menos anticipacion (HU-32). */
    static final Duration ANTICIPACION_MINIMA = Duration.ofHours(4);

    // La hora de la cita es la de la clinica. El contenedor corre en UTC, asi
    // que "ahora" se pide en la zona de El Salvador, igual que los cron de los
    // recordatorios en EventProcessorService.
    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");

    private final CitaRepository citaRepository;
    private final TipoCitaRepository tipoCitaRepository;
    private final UserRepository userRepository;
    private final PacienteRepository pacienteRepository;
    private final ClinicaRepository clinicaRepository;
    private final CitaMapper citaMapper;
    private final EventTypeRepository eventTypeRepository;
    private final EventRepository eventRepository;
    private final EventStatusRepository eventStatusRepository;
    private final HorarioDeMedicoService horarios;

    public CitaResponseDto crear(CitaRequestDto dto) {
        LocalDateTime fecha = leerFecha(dto.fechaHora());
        validarHorario(fecha);
        validarFutura(fecha);

        Paciente paciente = obtenerPaciente(dto.pacienteId());
        User medico = obtenerMedico(dto.medicoId());
        Clinicas clinica = obtenerClinica(dto.clinicaId());
        TipoCita tipoCita = obtenerTipo(dto.tipoCitaId());

        validarHorarioDelMedico(medico, clinica, fecha);
        validarDisponibilidad(paciente.getPersonaId(), medico.getUserID(), fecha, null);

        Cita cita = Cita.builder()
                .paciente(paciente)
                .medico(medico)
                .clinica(clinica)
                .tipo(tipoCita)
                .fechaHora(fecha)
                .estado(Cita.AGENDADA)
                .build();

        cita = guardar(cita);

        // Sin correo no hay a quien avisar, y un evento sin destinatario se
        // quedaria PENDIENTE reintentandose cada 15 segundos.
        String correo = paciente.getPersona().getEmail();
        if (correo != null && !correo.isBlank()) {
            Event event = Event.builder()
                    .eventType(
                            eventTypeRepository.getReferenceById(EventCodeEnums.APPOINTMENT_CREATED.getId())
                    )
                    .description("Se ha realizado una cita")
                    .eventStatus(
                            eventStatusRepository.getReferenceById(EventStatusEnums.PENDING.getId())
                    )
                    .userEmail(correo)
                    .ref(String.valueOf(cita.getCitaId()))
                    .createdAt(LocalDateTime.now())
                    .build();

            eventRepository.save(event);
        }
        return citaMapper.toResponse(cita);
    }

    @Transactional(readOnly = true)
    public List<CitaResponseDto> obtenerPorPaciente(Long pacienteId) {

        return citaRepository
                .findByPacienteOrderByFechaHoraAsc(pacienteId)
                .stream()
                .map(citaMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CitaResponseDto> obtenerPorMedico(Integer medicoId) {

        return citaRepository
                .findByMedico_UserIDOrderByFechaHoraAsc(medicoId)
                .stream()
                .map(citaMapper::toResponse)
                .toList();
    }

    /**
     * La agenda de una sede o de un medico entre dos dias, ambos incluidos.
     * Hace falta al menos uno de los dos filtros.
     */
    @Transactional(readOnly = true)
    public List<CitaResponseDto> agenda(Integer clinicaId, Integer medicoId, LocalDate desde, LocalDate hasta) {
        if (clinicaId == null && medicoId == null) {
            throw new GeneralException("Indique la clínica o el médico de la agenda", "400");
        }
        if (hasta.isBefore(desde)) {
            throw new GeneralException("La fecha final no puede ser anterior a la inicial", "400");
        }
        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.plusDays(1).atStartOfDay();

        List<Cita> citas = clinicaId != null
                ? citaRepository.agendaDeClinica(clinicaId, inicio, fin)
                : citaRepository.agendaDeMedico(medicoId, inicio, fin);

        return citas.stream()
                .filter(c -> medicoId == null || c.getMedico().getUserID().equals(medicoId))
                .map(citaMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MedicoDeAgendaDto> medicosDeClinica(Integer clinicaId) {
        obtenerClinica(clinicaId);
        return userRepository.medicosDeClinica(clinicaId).stream()
                .map(u -> new MedicoDeAgendaDto(
                        u.getUserID(),
                        (u.getPersona().getNombres() + " " + u.getPersona().getApellidos()).trim()))
                .sorted(Comparator.comparing(MedicoDeAgendaDto::nombre))
                .toList();
    }

    @Transactional(readOnly = true)
    public CitaResponseDto obtenerPorId(Integer id) {
        return citaMapper.toResponse(obtenerCita(id));
    }

    /**
     * Reprogramar (fecha, medico, tipo) o marcar atendida. Reprogramar repite
     * las validaciones de agendar (HU-32, criterio 3) y respeta la misma
     * anticipacion minima que cancelar (criterio 2).
     */
    public CitaResponseDto actualizar(
            Integer id,
            CitaUpdateRequestDto dto
    ) {

        Cita cita = obtenerCita(id);

        if (Cita.CANCELADA.equals(cita.getEstado())) {
            throw new GeneralException("La cita está cancelada y ya no se puede modificar", "409");
        }

        boolean cambiaMedico = dto.medicoId() != null && dto.medicoId() > 0
                && !dto.medicoId().equals(cita.getMedico().getUserID());
        boolean cambiaFecha = dto.fechaHora() != null && !dto.fechaHora().isBlank();

        if (cambiaMedico || cambiaFecha) {
            exigirAnticipacion(cita, "reprogramar");

            User medico = cambiaMedico ? obtenerMedico(dto.medicoId()) : cita.getMedico();
            LocalDateTime fecha = cita.getFechaHora();
            if (cambiaFecha) {
                fecha = leerFecha(dto.fechaHora());
                validarHorario(fecha);
                validarFutura(fecha);
            }

            if (cita.getClinica() != null) {
                validarHorarioDelMedico(medico, cita.getClinica(), fecha);
            }
            validarDisponibilidad(cita.getPaciente().getPersonaId(), medico.getUserID(), fecha, cita.getCitaId());

            cita.setMedico(medico);
            cita.setFechaHora(fecha);
        }

        if (dto.tipoCitaId() != null && dto.tipoCitaId() > 0) {
            cita.setTipo(obtenerTipo(dto.tipoCitaId()));
        }

        if (dto.estado() != null && !dto.estado().isBlank()) {
            String estado = dto.estado().trim().toUpperCase();
            if (Cita.CANCELADA.equals(estado)) {
                throw new GeneralException(
                        "Para cancelar una cita use la cancelación, que exige el motivo", "400");
            }
            if (!Cita.AGENDADA.equals(estado) && !Cita.ATENDIDA.equals(estado)) {
                throw new GeneralException("Estado de cita no válido: " + dto.estado(), "400");
            }
            cita.setEstado(estado);
        }

        // El paciente nunca se modifica

        return citaMapper.toResponse(guardar(cita));
    }

    /**
     * Cancelar con motivo (HU-32, criterios 1, 2 y 5). La cita no se borra:
     * queda CANCELADA con su motivo y libera la franja.
     */
    public CitaResponseDto cancelar(Integer id, String motivo) {
        Cita cita = obtenerCita(id);

        if (Cita.CANCELADA.equals(cita.getEstado())) {
            throw new GeneralException("La cita ya estaba cancelada", "409");
        }
        if (Cita.ATENDIDA.equals(cita.getEstado())) {
            throw new GeneralException("La cita ya fue atendida y no se puede cancelar", "409");
        }
        exigirAnticipacion(cita, "cancelar");

        cita.setEstado(Cita.CANCELADA);
        cita.setMotivoCancelacion(motivo.trim());
        return citaMapper.toResponse(guardar(cita));
    }

    private Cita guardar(Cita cita) {
        try {
            return citaRepository.saveAndFlush(cita);
        } catch (DataIntegrityViolationException e) {
            // El indice unico de V24: otra reserva tomo la franja entre la
            // validacion y el guardado (HU-30, criterio 5).
            throw new GeneralException(
                    "Ese horario acaba de ser reservado por otra persona. Elija otro.", "409");
        }
    }

    private Cita obtenerCita(Integer id) {
        return citaRepository.findById(id)
                .orElseThrow(() -> new GeneralException("Cita no encontrada", "404"));
    }

    private Paciente obtenerPaciente(Long pacienteId) {
        Paciente paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new GeneralException("Paciente no encontrado", "404"));
        if (!Paciente.ESTADO_ACTIVO.equals(paciente.getEstado())) {
            throw new GeneralException("El paciente está dado de baja y no se le pueden agendar citas", "409");
        }
        return paciente;
    }

    private User obtenerMedico(Integer medicoId) {
        User medico = userRepository.findById(medicoId)
                .orElseThrow(() -> new GeneralException("Médico no encontrado", "404"));
        boolean esMedico = medico.getRoles().stream()
                .anyMatch(r -> RolesEnum.MEDICO.getName().equals(r.getName()));
        if (!esMedico) {
            throw new GeneralException("El usuario elegido no es médico", "400");
        }
        return medico;
    }

    private Clinicas obtenerClinica(Integer clinicaId) {
        Clinicas clinica = clinicaRepository.findById(clinicaId)
                .orElseThrow(() -> new GeneralException("Clínica no encontrada", "404"));
        if (!Clinicas.ESTADO_ACTIVA.equals(clinica.getEstado())) {
            throw new GeneralException("La clínica está inactiva y no recibe citas", "409");
        }
        return clinica;
    }

    private TipoCita obtenerTipo(Integer tipoCitaId) {
        return tipoCitaRepository.findById(tipoCitaId)
                .orElseThrow(() -> new GeneralException("Tipo de cita no encontrado", "404"));
    }

    private static LocalDateTime leerFecha(String texto) {
        try {
            return LocalDateTime.parse(texto);
        } catch (DateTimeParseException e) {
            throw new GeneralException("La fecha y hora deben tener el formato AAAA-MM-DDTHH:MM", "400");
        }
    }

    private void validarHorario(LocalDateTime fechaHora) {

        if (fechaHora.getMinute() % DURACION_CITA_MINUTOS != 0) {
            throw new GeneralException(
                    "Las citas solamente pueden programarse cada 30 minutos", "400"
            );
        }

        if (fechaHora.getSecond() != 0 ||
                fechaHora.getNano() != 0) {

            throw new GeneralException(
                    "La hora de la cita debe estar en un intervalo exacto", "400"
            );
        }
    }

    /** HU-30, criterio 3: dentro del horario del medico en esa sede (HU-29). */
    private void validarHorarioDelMedico(User medico, Clinicas clinica, LocalDateTime fecha) {
        if (!horarios.atiende(medico.getUserID(), clinica.getClinicaId(), fecha)) {
            throw new GeneralException(
                    "El médico no atiende en " + clinica.getName() + " en ese día y hora", "400");
        }
    }

    /** Las horas libres de un medico en una sede un dia (HU-30, criterio 1). */
    @Transactional(readOnly = true)
    public List<String> franjasLibres(Integer clinicaId, Integer medicoId, LocalDate fecha) {
        return horarios.franjasLibres(clinicaId, medicoId, fecha);
    }

    private static void validarFutura(LocalDateTime fechaHora) {
        if (!fechaHora.isAfter(LocalDateTime.now(ZONA))) {
            throw new GeneralException("No se puede agendar una cita en una fecha u hora que ya pasó", "400");
        }
    }

    private static void exigirAnticipacion(Cita cita, String accion) {
        LocalDateTime limite = LocalDateTime.now(ZONA).plus(ANTICIPACION_MINIMA);
        if (cita.getFechaHora().isBefore(limite)) {
            throw new GeneralException(
                    "No se puede " + accion + " una cita con menos de 4 horas de anticipación", "409");
        }
    }

    private void validarDisponibilidad(
            Long pacienteId,
            Integer medicoId,
            LocalDateTime fechaHora,
            Integer citaIdActual
    ) {
        int excluir = citaIdActual == null ? -1 : citaIdActual;

        if (citaRepository.medicoOcupado(medicoId, fechaHora, excluir)) {
            throw new GeneralException(
                    "El médico ya tiene una cita en ese horario", "409"
            );
        }

        if (citaRepository.pacienteOcupado(pacienteId, fechaHora, excluir)) {
            throw new GeneralException(
                    "El paciente ya tiene una cita en ese horario", "409"
            );
        }
    }
}
