
package ues.edu.sv.education.service.tipohabito;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.tipohabito.TipoHabitoRequest;
import ues.edu.sv.education.model.dto.tipohabito.TipoHabitoResponse;
import ues.edu.sv.education.model.entity.TipoHabito;
import ues.edu.sv.education.model.mappers.TipoHabitoMapper;
import ues.edu.sv.education.repository.TipoHabitoRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TipoHabitoService {

    private final TipoHabitoRepository tipoHabitoRepository;
    private final TipoHabitoMapper tipoHabitoMapper;

    @Transactional
    public TipoHabitoResponse crear(TipoHabitoRequest request) {
        TipoHabito tipoHabito = tipoHabitoMapper.toEntity(request);

        return tipoHabitoMapper.toResponse(
                tipoHabitoRepository.save(tipoHabito)
        );
    }

    public List<TipoHabitoResponse> listar() {
        return tipoHabitoRepository.findAll()
                .stream()
                .map(tipoHabitoMapper::toResponse)
                .toList();
    }

    public TipoHabitoResponse buscarPorId(Integer id) {
        return tipoHabitoMapper.toResponse(obtenerEntidad(id));
    }

    @Transactional
    public TipoHabitoResponse actualizar(
            Integer id,
            TipoHabitoRequest request) {

        TipoHabito tipoHabito = obtenerEntidad(id);
        tipoHabito.setDescripcion(request.getDescripcion());

        return tipoHabitoMapper.toResponse(
                tipoHabitoRepository.save(tipoHabito)
        );
    }

    @Transactional
    public void eliminar(Integer id) {
        TipoHabito tipoHabito = obtenerEntidad(id);

        try {
            tipoHabitoRepository.delete(tipoHabito);
            tipoHabitoRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new GeneralException(
                    "No se puede eliminar el tipo de hábito porque tiene registros asociados",
                    "TIPO_HABITO_IN_USE"
            );
        }
    }

    private TipoHabito obtenerEntidad(Integer id) {
        return tipoHabitoRepository.findById(id)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontró el tipo de hábito con ID: " + id,
                        "TIPO_HABITO_NOT_FOUND"
                ));
    }
}
