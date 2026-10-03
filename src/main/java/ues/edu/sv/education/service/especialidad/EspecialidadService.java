package ues.edu.sv.education.service.especialidad;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.model.dto.especialidad.EspecialidadResponseDto;
import ues.edu.sv.education.repository.EspecialidadRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EspecialidadService {

    private final EspecialidadRepository especialidadRepository;

    // Catalogo que consume el formulario de registro del frontend: solo
    // las activas, para no ofrecer especialidades dadas de baja.
    @Transactional(readOnly = true)
    public List<EspecialidadResponseDto> listarActivas() {
        return especialidadRepository.findByActivaTrueOrderByNombre()
                .stream()
                .map(e -> new EspecialidadResponseDto(e.getEspecialidadId(), e.getNombre(), e.isActiva()))
                .toList();
    }
}
