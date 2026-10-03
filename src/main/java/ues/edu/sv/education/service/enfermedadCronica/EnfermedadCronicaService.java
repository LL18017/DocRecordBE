package ues.edu.sv.education.service.enfermedadCronica;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.EnfermedadCronica.EnfermedadCronicaRequest;
import ues.edu.sv.education.model.dto.EnfermedadCronica.EnfermedadCronicaResponse;
import ues.edu.sv.education.model.dto.EnfermedadCronica.EnfermedadCronicaUpdateRequest;
import ues.edu.sv.education.model.entity.EnfermedadCronica;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.mappers.EnfermedadCronicaMapper;
import ues.edu.sv.education.repository.EnfermedadCronicaRepository;
import ues.edu.sv.education.repository.UserRepository;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class EnfermedadCronicaService {

    private final EnfermedadCronicaRepository enfermedadCronicaRepository;
    private final UserRepository userRepository;

    public EnfermedadCronicaResponse crear(
            EnfermedadCronicaRequest request
    ) {

        User user = userRepository.findById(request.userId())
                .orElseThrow(() ->
                        new NoResourceFoundException("Usuario no encontrado","404")
                );

        EnfermedadCronica enfermedad = EnfermedadCronicaMapper.toEntity(
                request,
                user
        );

        EnfermedadCronica guardada =
                enfermedadCronicaRepository.save(enfermedad);

        return EnfermedadCronicaMapper.toResponse(guardada);
    }

    @Transactional(readOnly = true)
    public EnfermedadCronicaResponse obtenerPorId(
            Integer id
    ) {

        EnfermedadCronica enfermedad =
                enfermedadCronicaRepository.findById(id)
                        .orElseThrow(() ->
                                new NoResourceFoundException(
                                        "Enfermedad crónica no encontrada","404"
                                )
                        );

        return EnfermedadCronicaMapper.toResponse(enfermedad);
    }

    @Transactional(readOnly = true)
    public Set<EnfermedadCronicaResponse> obtenerPorUsuario(
            Integer userId
    ) {

        return enfermedadCronicaRepository
                .findByUserID(userId)
                .stream()
                .map(EnfermedadCronicaMapper::toResponse)
                .collect(Collectors.toSet());
    }

    public EnfermedadCronicaResponse actualizar(
            Integer id,
            EnfermedadCronicaUpdateRequest request
    ) {

        EnfermedadCronica enfermedad =
                enfermedadCronicaRepository.findById(id)
                        .orElseThrow(() ->
                                new NoResourceFoundException(
                                        "Enfermedad crónica no encontrada","404"
                                )
                        );

        EnfermedadCronicaMapper.updateEntity(
                enfermedad,
                request
        );

        EnfermedadCronica actualizada =
                enfermedadCronicaRepository.save(enfermedad);

        return EnfermedadCronicaMapper.toResponse(actualizada);
    }

    public void eliminar(Integer id) {

        if (!enfermedadCronicaRepository.existsById(id)) {
            throw new NoResourceFoundException(
                    "Enfermedad crónica no encontrada","404"
            );
        }

        enfermedadCronicaRepository.deleteById(id);
    }
}