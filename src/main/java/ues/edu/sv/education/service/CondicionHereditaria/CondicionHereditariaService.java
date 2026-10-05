package ues.edu.sv.education.service.CondicionHereditaria;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.CondicionHereditaria.CondicionHereditariaRequest;
import ues.edu.sv.education.model.dto.CondicionHereditaria.CondicionHereditariaResponse;
import ues.edu.sv.education.model.dto.CondicionHereditaria.CondicionHereditariaUpdateRequest;
import ues.edu.sv.education.model.entity.CondicionHereditaria;
import ues.edu.sv.education.model.entity.Paciente;
import ues.edu.sv.education.model.mappers.CondicionHereditariaMapper;
import ues.edu.sv.education.repository.CondicionHereditariaRepository;
import ues.edu.sv.education.repository.PacienteRepository;

import java.util.Comparator;
import java.util.List;

/**
 * HU-13 · Condiciones hereditarias del expediente.
 *
 * El listado sale ordenado por parentesco -en el orden del enum: padres,
 * abuelos, hermanos, otro- y dentro de cada parentesco por nombre. Es el
 * orden en que el expediente las agrupa (criterio 3); agruparlas es trabajo
 * de la pantalla, ordenarlas no.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CondicionHereditariaService {

    private static final Comparator<CondicionHereditaria> POR_PARENTESCO_Y_NOMBRE =
            Comparator.comparing(CondicionHereditaria::getParentesco)
                    .thenComparing(c -> c.getNombre().toLowerCase());

    private final CondicionHereditariaRepository condicionHereditariaRepository;
    private final PacienteRepository pacienteRepository;

    public CondicionHereditariaResponse crear(CondicionHereditariaRequest request) {
        Paciente paciente = exigirPaciente(request.pacienteId());
        CondicionHereditaria guardada = condicionHereditariaRepository.save(
                CondicionHereditariaMapper.toEntity(request, paciente));
        return CondicionHereditariaMapper.toResponse(guardada);
    }

    @Transactional(readOnly = true)
    public CondicionHereditariaResponse obtenerPorId(Integer id) {
        return CondicionHereditariaMapper.toResponse(exigirCondicion(id));
    }

    /** Lista vacia si no tiene ninguna; 404 si el paciente no existe. */
    @Transactional(readOnly = true)
    public List<CondicionHereditariaResponse> listarDelPaciente(Long pacienteId) {
        exigirPaciente(pacienteId);
        return condicionHereditariaRepository.findByPaciente_PersonaId(pacienteId)
                .stream()
                .sorted(POR_PARENTESCO_Y_NOMBRE)
                .map(CondicionHereditariaMapper::toResponse)
                .toList();
    }

    public CondicionHereditariaResponse actualizar(Integer id, CondicionHereditariaUpdateRequest request) {
        CondicionHereditaria condicion = exigirCondicion(id);
        CondicionHereditariaMapper.updateEntity(condicion, request);
        return CondicionHereditariaMapper.toResponse(condicion);
    }

    public void eliminar(Integer id) {
        condicionHereditariaRepository.delete(exigirCondicion(id));
    }

    private Paciente exigirPaciente(Long pacienteId) {
        return pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontro el paciente con id: " + pacienteId, "404"));
    }

    private CondicionHereditaria exigirCondicion(Integer id) {
        return condicionHereditariaRepository.findById(id)
                .orElseThrow(() -> new NoResourceFoundException("Condición hereditaria no encontrada", "404"));
    }
}
