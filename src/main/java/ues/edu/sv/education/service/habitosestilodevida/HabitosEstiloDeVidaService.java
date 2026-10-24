
package ues.edu.sv.education.service.habitosestilodevida;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.habitosestilodevida.HabitosEstiloDeVidaRequest;
import ues.edu.sv.education.model.dto.habitosestilodevida.HabitosEstiloDeVidaResponse;
import ues.edu.sv.education.model.entity.HabitosEstiloDeVida;
import ues.edu.sv.education.model.entity.Paciente;
import ues.edu.sv.education.model.entity.TipoHabito;
import ues.edu.sv.education.model.mappers.HabitosEstiloDeVidaMapper;
import ues.edu.sv.education.repository.HabitosEstiloDeVidaRepository;
import ues.edu.sv.education.repository.PacienteRepository;
import ues.edu.sv.education.repository.TipoHabitoRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HabitosEstiloDeVidaService {

    private final HabitosEstiloDeVidaRepository habitosRepository;
    private final PacienteRepository pacientesRepository;
    private final TipoHabitoRepository tipoHabitoRepository;
    private final HabitosEstiloDeVidaMapper habitosMapper;

    @Transactional
    public HabitosEstiloDeVidaResponse crear(
            HabitosEstiloDeVidaRequest request) {

        Paciente paciente = obtenerPaciente(request.getPacienteId());
        TipoHabito tipoHabito =
                obtenerTipoHabito(request.getTipoHabitoId());

        HabitosEstiloDeVida habito =
                habitosMapper.toEntity(request, paciente, tipoHabito);

        return habitosMapper.toResponse(
                habitosRepository.save(habito)
        );
    }

    public List<HabitosEstiloDeVidaResponse> listar() {
        return habitosRepository.findAll()
                .stream()
                .map(habitosMapper::toResponse)
                .toList();
    }

    public HabitosEstiloDeVidaResponse buscarPorId(Long id) {
        return habitosMapper.toResponse(obtenerHabito(id));
    }

    public List<HabitosEstiloDeVidaResponse> listarPorPaciente(
            Long pacienteId) {

        // Verifica que el paciente exista, aunque no tenga hábitos.
        obtenerPaciente(pacienteId);

        return habitosRepository
                .findByPaciente_PersonaId(pacienteId)
                .stream()
                .map(habitosMapper::toResponse)
                .toList();
    }

    @Transactional
    public HabitosEstiloDeVidaResponse actualizar(
            Long id,
            HabitosEstiloDeVidaRequest request) {

        HabitosEstiloDeVida habito = obtenerHabito(id);
        Paciente paciente = obtenerPaciente(request.getPacienteId());
        TipoHabito tipoHabito =
                obtenerTipoHabito(request.getTipoHabitoId());

        habito.setDescripcion(request.getDescripcion());
        habito.setNivel(request.getNivel());
        habito.setPaciente(paciente);
        habito.setTipoHabito(tipoHabito);

        return habitosMapper.toResponse(
                habitosRepository.save(habito)
        );
    }

    @Transactional
    public void eliminar(Long id) {
        HabitosEstiloDeVida habito = obtenerHabito(id);

        try {
            habitosRepository.delete(habito);
            habitosRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new GeneralException(
                    "No se puede eliminar el hábito debido a restricciones de integridad",
                    "HABITO_DELETE_ERROR"
            );
        }
    }

    private HabitosEstiloDeVida obtenerHabito(Long id) {
        return habitosRepository.findById(id)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontró el hábito con ID: " + id,
                        "HABITO_NOT_FOUND"
                ));
    }

    private Paciente obtenerPaciente(Long id) {
        return pacientesRepository.findByPersonaId(id)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontró el paciente con ID: " + id,
                        "PACIENTE_NOT_FOUND"
                ));
    }

    private TipoHabito obtenerTipoHabito(Integer id) {
        return tipoHabitoRepository.findById(id)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontró el tipo de hábito con ID: " + id,
                        "TIPO_HABITO_NOT_FOUND"
                ));
    }
}
