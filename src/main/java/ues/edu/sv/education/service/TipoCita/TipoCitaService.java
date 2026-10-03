package ues.edu.sv.education.service.TipoCita;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.model.dto.tipoCita.TipoCitaRequestDto;
import ues.edu.sv.education.model.dto.tipoCita.TipoCitaResponseDto;
import ues.edu.sv.education.model.entity.TipoCita;
import ues.edu.sv.education.model.mappers.TipoCitaMapper;
import ues.edu.sv.education.repository.TipoCitaRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TipoCitaService {

    private final TipoCitaRepository tipoCitaRepository;
    private final TipoCitaMapper tipoCitaMapper;

    public TipoCitaResponseDto crear(TipoCitaRequestDto dto) {

        TipoCita tipoCita = tipoCitaMapper.toEntity(dto);

        tipoCita = tipoCitaRepository.save(tipoCita);

        return tipoCitaMapper.toResponse(tipoCita);
    }

    @Transactional(readOnly = true)
    public List<TipoCitaResponseDto> obtenerTodos() {

        return tipoCitaRepository.findAll()
                .stream()
                .map(tipoCitaMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TipoCitaResponseDto obtenerPorId(Integer id) {

        TipoCita tipoCita = tipoCitaRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Tipo de cita no encontrado")
                );

        return tipoCitaMapper.toResponse(tipoCita);
    }

    public TipoCitaResponseDto actualizar(
            Integer id,
            TipoCitaRequestDto dto
    ) {

        TipoCita tipoCita = tipoCitaRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Tipo de cita no encontrado")
                );

        tipoCita.setNombre(dto.nombre());
        tipoCita.setDescripcion(dto.descripcion());

        return tipoCitaMapper.toResponse(tipoCita);
    }

    public void eliminar(Integer id) {

        if (!tipoCitaRepository.existsById(id)) {
            throw new RuntimeException("Tipo de cita no encontrado");
        }

        tipoCitaRepository.deleteById(id);
    }
}