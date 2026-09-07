package ues.edu.sv.education.service.Alergia;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.Alergia.AlergiaRequest;
import ues.edu.sv.education.model.dto.Alergia.AlergiaResponse;
import ues.edu.sv.education.model.dto.Alergia.AlergiaUpdateRequest;
import ues.edu.sv.education.model.entity.Alergia;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.mappers.AlergiaMapper;
import ues.edu.sv.education.repository.AlergiaRepository;
import ues.edu.sv.education.repository.UserRepository;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AlergiaService {

    private final AlergiaRepository alergiaRepository;
    private final UserRepository userRepository;

    public AlergiaResponse crear(AlergiaRequest request) {

        User user = userRepository.findById(request.userId())
                .orElseThrow(() ->
                        new NoResourceFoundException("Usuario no encontrado","404")
                );

        Alergia alergia = AlergiaMapper.toEntity(
                request,
                user
        );

        Alergia guardada = alergiaRepository.save(alergia);

        return AlergiaMapper.toResponse(guardada);
    }

    @Transactional(readOnly = true)
    public AlergiaResponse obtenerPorId(Integer id) {

        Alergia alergia = alergiaRepository.findById(id)
                .orElseThrow(() ->
                        new NoResourceFoundException("Alergia no encontrada","404")
                );

        return AlergiaMapper.toResponse(alergia);
    }

    @Transactional(readOnly = true)
    public Set<AlergiaResponse> obtenerPorUsuario(Integer userId) {

        return alergiaRepository.findByUserID(userId)
                .stream()
                .map(AlergiaMapper::toResponse)
                .collect(Collectors.toSet());
    }

    public AlergiaResponse actualizar(
            Integer id,
            AlergiaUpdateRequest request
    ) {

        Alergia alergia = alergiaRepository.findById(id)
                .orElseThrow(() ->
                        new NoResourceFoundException("Alergia no encontrada","404")
                );

        AlergiaMapper.updateEntity(
                alergia,
                request
        );

        Alergia actualizada = alergiaRepository.save(alergia);

        return AlergiaMapper.toResponse(actualizada);
    }

    public void eliminar(Integer id) {

        if (!alergiaRepository.existsById(id)) {
            throw new NoResourceFoundException("Alergia no encontrada","404");
        }

        alergiaRepository.deleteById(id);
    }
}