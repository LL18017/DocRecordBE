package ues.edu.sv.education.service.Clinicas;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.clinicas.ClinicasRequestDto;
import ues.edu.sv.education.model.dto.clinicas.ClinicasResponseDto;
import ues.edu.sv.education.model.entity.Clinicas;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.repository.ClinicaRepository;
import ues.edu.sv.education.repository.UserRepository;
import ues.edu.sv.education.repository.UserTypeRepository;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ClinicaService {
    private final UserRepository userRepository;
    private final UserTypeRepository userTypeRepository;
    private final ClinicaRepository clinicasRepository;

    @Transactional(readOnly = true)
    public List<ClinicasResponseDto> obtenerPorUsuario(Integer userId) {

        return clinicasRepository.findByUser(userId)
                .stream()
                .map(this::toResponseDto)
                .toList();
    }

    @Transactional
    public ClinicasResponseDto crear(ClinicasRequestDto dto) {

        User user = obtenerUsuarioAutorizado(dto.userId());

        Clinicas clinica = Clinicas.builder()
                .name(dto.name())
                .latitud(dto.latitud())
                .longitud(dto.longitud())
                .user(user)
                .build();

        clinicasRepository.save(clinica);

        return toResponseDto(clinica);
    }

    @Transactional
    public ClinicasResponseDto editar(
            Integer clinicaId,
            ClinicasRequestDto dto
    ) {

        Clinicas clinica = clinicasRepository.findById(clinicaId)
                .orElseThrow(() ->
                        new RuntimeException("Clínica no encontrada")
                );

        User user = obtenerUsuarioAutorizado(dto.userId());

        clinica.setName(dto.name());
        clinica.setLatitud(dto.latitud());
        clinica.setLongitud(dto.longitud());
        clinica.setUser(user);

        return toResponseDto(clinicasRepository.save(clinica));
    }

    @Transactional
    public void eliminar(Integer clinicaId, Integer userId) {

        User user = obtenerUsuarioAutorizado(userId);

        Clinicas clinica = clinicasRepository.findById(clinicaId)
                .orElseThrow(() ->
                        new NoResourceFoundException("Clínica no encontrada")
                );

        if (!clinica.getUser().getUserID().equals(user.getUserID())) {
            throw new GeneralException(
                    "El usuario no tiene permiso para eliminar esta clínica"
            );
        }

        clinicasRepository.delete(clinica);
    }

    private User obtenerUsuarioAutorizado(Integer userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                   new NoResourceFoundException("Usuario no encontrado","404")
                );

        Integer userTypeId = user.getUserType().getUserTypeID();

        if (userTypeId != 1 && userTypeId != 2) {
            throw new NoResourceFoundException(
                    "El usuario no tiene permisos para administrar clínicas","403"
            );
        }

        return user;
    }

    private ClinicasResponseDto toResponseDto(Clinicas clinica) {

        return new ClinicasResponseDto(
                clinica.getClinicaId(),
                clinica.getName(),
                clinica.getLatitud(),
                clinica.getLongitud()
        );
    }
}
