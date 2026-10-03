package ues.edu.sv.education.service.CondicionHereditaria;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.CondicionHereditaria.CondicionHereditariaRequest;
import ues.edu.sv.education.model.dto.CondicionHereditaria.CondicionHereditariaResponse;
import ues.edu.sv.education.model.dto.CondicionHereditaria.CondicionHereditariaUpdateRequest;
import ues.edu.sv.education.model.entity.CondicionHereditaria;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.mappers.CondicionHereditariaMapper;
import ues.edu.sv.education.repository.CondicionHereditariaRepository;
import ues.edu.sv.education.repository.UserRepository;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CondicionHereditariaService {

    private final CondicionHereditariaRepository condicionHereditariaRepository;
    private final UserRepository userRepository;

    public CondicionHereditariaResponse crear(
            CondicionHereditariaRequest request
    ) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NoResourceFoundException("Usuario no encontrado","404"));

        CondicionHereditaria condicion = CondicionHereditariaMapper.toEntity(
                request,
                user
        );

        CondicionHereditaria guardada =
                condicionHereditariaRepository.save(condicion);

        return CondicionHereditariaMapper.toResponse(guardada);
    }

    @Transactional(readOnly = true)
    public CondicionHereditariaResponse obtenerPorId(Integer id) {

        CondicionHereditaria condicion =
                condicionHereditariaRepository.findById(id)
                        .orElseThrow(() ->
                                new NoResourceFoundException(
                                        "Condición hereditaria no encontrada","404"
                                )
                        );

        return CondicionHereditariaMapper.toResponse(condicion);
    }

    @Transactional(readOnly = true)
    public Set<CondicionHereditariaResponse> obtenerPorUsuario(
            Integer userId
    ) {
        return condicionHereditariaRepository.findByUserID(userId)
                .stream()
                .map(CondicionHereditariaMapper::toResponse)
                .collect(Collectors.toSet());
    }

    public CondicionHereditariaResponse actualizar(
            Integer id,
            CondicionHereditariaUpdateRequest request
    ) {
        CondicionHereditaria condicion =
                condicionHereditariaRepository.findById(id)
                        .orElseThrow(() ->
                                new NoResourceFoundException(
                                        "Condición hereditaria no encontrada","404"
                                )
                        );

        CondicionHereditariaMapper.updateEntity(
                condicion,
                request
        );

        CondicionHereditaria actualizada =
                condicionHereditariaRepository.save(condicion);

        return CondicionHereditariaMapper.toResponse(actualizada);
    }

    public void eliminar(Integer id) {

        if (!condicionHereditariaRepository.existsById(id)) {
            throw new NoResourceFoundException(
                    "Condición hereditaria no encontrada","404"
            );
        }

        condicionHereditariaRepository.deleteById(id);
    }
}