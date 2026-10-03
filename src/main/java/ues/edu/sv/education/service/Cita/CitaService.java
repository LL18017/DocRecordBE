package ues.edu.sv.education.service.Cita;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.model.dto.cita.CitaRequestDto;
import ues.edu.sv.education.model.dto.cita.CitaResponseDto;
import ues.edu.sv.education.model.dto.cita.CitaUpdateRequestDto;
import ues.edu.sv.education.model.entity.Cita;
import ues.edu.sv.education.model.entity.TipoCita;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.mappers.CitaMapper;
import ues.edu.sv.education.repository.CitaRepository;
import ues.edu.sv.education.repository.TipoCitaRepository;
import ues.edu.sv.education.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CitaService {

    private static final int DURACION_CITA_MINUTOS = 30;

    private final CitaRepository citaRepository;
    private final TipoCitaRepository tipoCitaRepository;
    private final UserRepository userRepository;
    private final CitaMapper citaMapper;

    public CitaResponseDto crear(CitaRequestDto dto) {
        LocalDateTime fecha = LocalDateTime.parse(dto.fechaHora());
        validarHorario(fecha);

        User paciente = obtenerUsuario(dto.pacienteId());

        User medico = obtenerUsuario(dto.medicoId());

        TipoCita tipoCita = tipoCitaRepository.findById(dto.tipoCitaId())
                .orElseThrow(() ->
                        new RuntimeException("Tipo de cita no encontrado")
                );

        validarDisponibilidad(
                dto.pacienteId(),
                dto.medicoId(),
                fecha
        );

        Cita cita = citaMapper.toEntity(
                dto,
                paciente,
                tipoCita,
                medico
        );

        cita = citaRepository.save(cita);

        return citaMapper.toResponse(cita);
    }

    @Transactional(readOnly = true)
    public List<CitaResponseDto> obtenerPorPaciente(Integer pacienteId) {

        return citaRepository
                .findByPaciente_UserIDOrderByFechaHoraAsc(pacienteId)
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

    @Transactional(readOnly = true)
    public CitaResponseDto obtenerPorId(Integer id) {

        Cita cita = citaRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Cita no encontrada")
                );

        return citaMapper.toResponse(cita);
    }

    public CitaResponseDto actualizar(
            Integer id,
            CitaUpdateRequestDto dto
    ) {

        Cita cita = citaRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Cita no encontrada")
                );

        // ==========================================
        // 1. MÉDICO
        // ==========================================

        User medico = cita.getMedico();

        if (dto.medicoId() != null && dto.medicoId() > 0) {

            medico = obtenerUsuario(dto.medicoId());
        }

        // ==========================================
        // 2. FECHA
        // ==========================================

        LocalDateTime fecha = cita.getFechaHora();

        if (dto.fechaHora() != null && !dto.fechaHora().isBlank()) {

            fecha = LocalDateTime.parse(dto.fechaHora());

            validarHorario(fecha);
        }

        // ==========================================
        // 3. VALIDAR DISPONIBILIDAD
        // ==========================================

        if ((dto.medicoId() != null && dto.medicoId() > 0)
                || (dto.fechaHora() != null && !dto.fechaHora().isBlank())) {

            validarDisponibilidad(
                    cita.getPaciente().getUserID(),
                    medico.getUserID(),
                    fecha,
                    id
            );
        }

        // ==========================================
        // 4. TIPO DE CITA
        // ==========================================

        if (dto.tipoCitaId() != null && dto.tipoCitaId() > 0) {

            TipoCita tipoCita = tipoCitaRepository.findById(dto.tipoCitaId())
                    .orElseThrow(() ->
                            new RuntimeException("Tipo de cita no encontrado")
                    );

            cita.setTipo(tipoCita);
        }

        // ==========================================
        // 5. ESTADO
        // ==========================================

        if (dto.estado() != null && !dto.estado().isBlank()) {
            cita.setEstado(dto.estado());
        }

        // ==========================================
        // 6. ACTUALIZAR
        // ==========================================

        cita.setMedico(medico);
        cita.setFechaHora(fecha);

        // El paciente nunca se modifica

        return citaMapper.toResponse(cita);
    }

    public void eliminar(Integer id) {

        if (!citaRepository.existsById(id)) {
            throw new RuntimeException("Cita no encontrada");
        }

        citaRepository.deleteById(id);
    }

    private User obtenerUsuario(Integer userId) {

        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Usuario no encontrado: " + userId
                        )
                );
    }

    private void validarHorario(LocalDateTime fechaHora) {

        if (fechaHora.getMinute() % DURACION_CITA_MINUTOS != 0) {
            throw new IllegalArgumentException(
                    "Las citas solamente pueden programarse cada 30 minutos"
            );
        }

        if (fechaHora.getSecond() != 0 ||
                fechaHora.getNano() != 0) {

            throw new IllegalArgumentException(
                    "La hora de la cita debe estar en un intervalo exacto"
            );
        }
    }

    private void validarDisponibilidad(
            Integer pacienteId,
            Integer medicoId,
            LocalDateTime fechaHora
    ) {

        validarDisponibilidad(
                pacienteId,
                medicoId,
                fechaHora,
                null
        );
    }

    private void validarDisponibilidad(
            Integer pacienteId,
            Integer medicoId,
            LocalDateTime fechaHora,
            Integer citaIdActual
    ) {

        boolean medicoOcupado =
                citaRepository.existsByMedico_UserIDAndFechaHora(
                        medicoId,
                        fechaHora
                );

        if (medicoOcupado &&
                !esMismaCita(medicoId, fechaHora, citaIdActual)) {

            throw new IllegalArgumentException(
                    "El médico ya tiene una cita en ese horario"
            );
        }

        boolean pacienteOcupado =
                citaRepository.existsByPaciente_UserIDAndFechaHora(
                        pacienteId,
                        fechaHora
                );

        if (pacienteOcupado &&
                !esMismaCitaPaciente(
                        pacienteId,
                        fechaHora,
                        citaIdActual
                )) {

            throw new IllegalArgumentException(
                    "El paciente ya tiene una cita en ese horario"
            );
        }
    }

    private boolean esMismaCita(
            Integer medicoId,
            LocalDateTime fechaHora,
            Integer citaId
    ) {

        if (citaId == null) {
            return false;
        }

        return citaRepository.findById(citaId)
                .map(cita ->
                        cita.getMedico().getUserID().equals(medicoId)
                                && cita.getFechaHora().equals(fechaHora)
                )
                .orElse(false);
    }

    private boolean esMismaCitaPaciente(
            Integer pacienteId,
            LocalDateTime fechaHora,
            Integer citaId
    ) {

        if (citaId == null) {
            return false;
        }

        return citaRepository.findById(citaId)
                .map(cita ->
                        cita.getPaciente().getUserID().equals(pacienteId)
                                && cita.getFechaHora().equals(fechaHora)
                )
                .orElse(false);
    }
}