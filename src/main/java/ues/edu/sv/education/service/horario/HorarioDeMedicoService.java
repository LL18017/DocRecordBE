package ues.edu.sv.education.service.horario;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.model.dto.cita.CitaResponseDto;
import ues.edu.sv.education.model.dto.horario.HorarioRequestDto;
import ues.edu.sv.education.model.dto.horario.HorarioResponseDto;
import ues.edu.sv.education.model.dto.horario.MedicoDeClinicaDto;
import ues.edu.sv.education.model.entity.Clinicas;
import ues.edu.sv.education.model.entity.HorarioDeMedico;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.enums.RolesEnum;
import ues.edu.sv.education.model.mappers.CitaMapper;
import ues.edu.sv.education.repository.CitaRepository;
import ues.edu.sv.education.repository.ClinicaRepository;
import ues.edu.sv.education.repository.HorarioDeMedicoRepository;
import ues.edu.sv.education.repository.MedicoRepository;
import ues.edu.sv.education.repository.UserRepository;
import ues.edu.sv.education.service.user.UserService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Medicos de una sede y sus horarios (HU-29), y lo que de eso necesita la
 * agenda (HU-30): en que franjas atiende un medico y si una hora cae dentro.
 *
 * Un medico SIN horarios en una sede no queda bloqueado: se le agenda en la
 * jornada por defecto (7:00 a 17:00). Asi las sedes que todavia no cargaron
 * horarios siguen funcionando; en cuanto un medico tiene al menos una franja
 * en la sede, solo se le agenda dentro de ellas.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class HorarioDeMedicoService {

    static final int MINUTOS_POR_CITA = 30;
    static final LocalTime JORNADA_INICIO = LocalTime.of(7, 0);
    static final LocalTime JORNADA_FIN = LocalTime.of(17, 0);
    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");
    private static final Locale ES = Locale.forLanguageTag("es-SV");

    private final HorarioDeMedicoRepository horarioRepository;
    private final UserRepository userRepository;
    private final ClinicaRepository clinicaRepository;
    private final MedicoRepository medicoRepository;
    private final CitaRepository citaRepository;
    private final CitaMapper citaMapper;
    private final UserService userService;

    /** La ficha de la sede: sus medicos con especialidad y horario (criterio 3). */
    @Transactional(readOnly = true)
    public List<MedicoDeClinicaDto> medicosDeLaClinica(Integer clinicaId) {
        obtenerClinica(clinicaId);
        Map<Integer, List<HorarioResponseDto>> horarios = horarioRepository.deLaClinica(clinicaId).stream()
                .collect(Collectors.groupingBy(h -> h.getMedico().getUserID(),
                        Collectors.mapping(HorarioDeMedicoService::aDto, Collectors.toList())));

        return userRepository.medicosDeClinica(clinicaId).stream()
                .map(u -> new MedicoDeClinicaDto(
                        u.getUserID(),
                        (u.getPersona().getNombres() + " " + u.getPersona().getApellidos()).trim(),
                        medicoRepository.findById(u.getPersona().getPersonaId())
                                .map(m -> m.getEspecialidad() != null ? m.getEspecialidad().getNombre() : null)
                                .orElse(null),
                        horarios.getOrDefault(u.getUserID(), List.of())))
                .sorted(Comparator.comparing(MedicoDeClinicaDto::nombre))
                .toList();
    }

    /**
     * Agrega una franja. Un medico puede atender en varias sedes siempre que
     * sus franjas no se traslapen (criterio 1); si se traslapan, se rechaza
     * nombrando la otra sede y la hora (criterio 2). Si el medico aun no
     * estaba asignado a la sede, queda asignado.
     */
    public HorarioResponseDto agregarHorario(Integer clinicaId, Integer medicoId, HorarioRequestDto dto) {
        Clinicas clinica = obtenerClinica(clinicaId);
        if (!Clinicas.ESTADO_ACTIVA.equals(clinica.getEstado())) {
            throw new GeneralException("La clínica está inactiva y no recibe horarios", "409");
        }
        User medico = obtenerMedico(medicoId);
        LocalTime inicio = leerHora(dto.horaInicio(), "inicio");
        LocalTime fin = leerHora(dto.horaFin(), "fin");
        if (!fin.isAfter(inicio)) {
            throw new GeneralException("La hora de fin debe ser posterior a la de inicio", "400");
        }
        short dia = dto.diaSemana().shortValue();

        for (HorarioDeMedico otro : horarioRepository.delMedicoElDia(medicoId, dia)) {
            if (inicio.isBefore(otro.getHoraFin()) && fin.isAfter(otro.getHoraInicio())) {
                throw new GeneralException(
                        "El médico ya atiende el " + nombreDelDia(dia) + " de " + otro.getHoraInicio()
                                + " a " + otro.getHoraFin() + " en " + otro.getClinica().getName()
                                + ": los horarios no pueden traslaparse", "409");
            }
        }

        asegurarAsignacion(medico, clinica);

        HorarioDeMedico horario = horarioRepository.save(HorarioDeMedico.builder()
                .medico(medico)
                .clinica(clinica)
                .diaSemana(dia)
                .horaInicio(inicio)
                .horaFin(fin)
                .build());
        return aDto(horario);
    }

    public void quitarHorario(Integer clinicaId, Integer horarioId) {
        HorarioDeMedico horario = horarioRepository.findById(horarioId)
                .filter(h -> h.getClinica().getClinicaId().equals(clinicaId))
                .orElseThrow(() -> new GeneralException("Horario no encontrado en esta clínica", "404"));
        horarioRepository.delete(horario);
    }

    /**
     * Las citas AGENDADAS a futuro del medico en la sede: lo que la pantalla
     * muestra antes de confirmar que se le quita la asignacion (criterio 4).
     */
    @Transactional(readOnly = true)
    public List<CitaResponseDto> citasFuturas(Integer clinicaId, Integer medicoId) {
        return citaRepository.futurasDelMedicoEnLaClinica(medicoId, clinicaId, LocalDateTime.now(ZONA)).stream()
                .map(citaMapper::toResponse)
                .toList();
    }

    /** Quita al medico de la sede, con sus franjas en ella. */
    public void quitarDeLaClinica(Integer clinicaId, Integer medicoId) {
        horarioRepository.deleteAll(horarioRepository.delMedicoEnLaClinica(clinicaId, medicoId));
        userService.quitarClinica(medicoId, clinicaId);
    }

    // ── Lo que usa la agenda (HU-30) ────────────────────────────────────────

    /**
     * Si la cita de `inicio` cabe en el horario del medico en la sede. Sin
     * horarios cargados en la sede, vale la jornada por defecto.
     */
    @Transactional(readOnly = true)
    public boolean atiende(Integer medicoId, Integer clinicaId, LocalDateTime inicio) {
        LocalTime desde = inicio.toLocalTime();
        LocalTime hasta = desde.plusMinutes(MINUTOS_POR_CITA);
        return franjasDeAtencion(medicoId, clinicaId, inicio.toLocalDate()).stream()
                .anyMatch(r -> !desde.isBefore(r[0]) && !hasta.isAfter(r[1]) && hasta.isAfter(desde));
    }

    /**
     * Las horas libres de un medico en una sede un dia: dentro de su horario,
     * sin las ocupadas por citas vivas y sin las que ya pasaron (criterio 1
     * de HU-30).
     */
    @Transactional(readOnly = true)
    public List<String> franjasLibres(Integer clinicaId, Integer medicoId, LocalDate fecha) {
        obtenerClinica(clinicaId);
        LocalDateTime ahora = LocalDateTime.now(ZONA);
        Set<LocalTime> ocupadas = new HashSet<>();
        citaRepository.agendaDeMedico(medicoId, fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay()).stream()
                .filter(c -> !"CANCELADA".equals(c.getEstado()))
                .forEach(c -> ocupadas.add(c.getFechaHora().toLocalTime()));

        List<String> libres = new ArrayList<>();
        for (LocalTime[] rango : franjasDeAtencion(medicoId, clinicaId, fecha)) {
            for (LocalTime t = rango[0];
                 !t.plusMinutes(MINUTOS_POR_CITA).isAfter(rango[1]) && t.plusMinutes(MINUTOS_POR_CITA).isAfter(t);
                 t = t.plusMinutes(MINUTOS_POR_CITA)) {
                if (!ocupadas.contains(t) && fecha.atTime(t).isAfter(ahora)) {
                    libres.add(t.toString());
                }
            }
        }
        return libres.stream().distinct().sorted().toList();
    }

    private List<LocalTime[]> franjasDeAtencion(Integer medicoId, Integer clinicaId, LocalDate fecha) {
        List<HorarioDeMedico> enLaSede = horarioRepository.delMedicoEnLaClinica(clinicaId, medicoId);
        if (enLaSede.isEmpty()) {
            return List.<LocalTime[]>of(new LocalTime[]{JORNADA_INICIO, JORNADA_FIN});
        }
        int dia = fecha.getDayOfWeek().getValue();
        return enLaSede.stream()
                .filter(h -> h.getDiaSemana() == dia)
                .map(h -> new LocalTime[]{h.getHoraInicio(), h.getHoraFin()})
                .toList();
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    private void asegurarAsignacion(User medico, Clinicas clinica) {
        boolean propia = medico.getClinicas() != null && medico.getClinicas().stream()
                .anyMatch(c -> Objects.equals(c.getClinicaId(), clinica.getClinicaId()));
        boolean asignada = medico.getClinicasAsignadas() != null && medico.getClinicasAsignadas().stream()
                .anyMatch(c -> Objects.equals(c.getClinicaId(), clinica.getClinicaId()));
        if (!propia && !asignada) {
            if (medico.getClinicasAsignadas() == null) {
                medico.setClinicasAsignadas(new HashSet<>());
            }
            medico.getClinicasAsignadas().add(clinica);
            userRepository.save(medico);
        }
    }

    private Clinicas obtenerClinica(Integer clinicaId) {
        return clinicaRepository.findById(clinicaId)
                .orElseThrow(() -> new GeneralException("Clínica no encontrada", "404"));
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

    private static LocalTime leerHora(String texto, String cual) {
        LocalTime hora;
        try {
            hora = LocalTime.parse(texto);
        } catch (DateTimeParseException e) {
            throw new GeneralException("La hora de " + cual + " debe tener el formato HH:MM", "400");
        }
        if (hora.getMinute() % MINUTOS_POR_CITA != 0 || hora.getSecond() != 0) {
            throw new GeneralException("Los horarios van en bloques de 30 minutos", "400");
        }
        return hora;
    }

    private static String nombreDelDia(short dia) {
        return java.time.DayOfWeek.of(dia).getDisplayName(TextStyle.FULL, ES);
    }

    private static HorarioResponseDto aDto(HorarioDeMedico h) {
        return new HorarioResponseDto(h.getHorarioId(), (int) h.getDiaSemana(),
                h.getHoraInicio().toString(), h.getHoraFin().toString());
    }
}
