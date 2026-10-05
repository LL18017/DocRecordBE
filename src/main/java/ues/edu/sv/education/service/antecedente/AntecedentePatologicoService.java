package ues.edu.sv.education.service.antecedente;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.antecedente.AntecedentePatologicoRequestDto;
import ues.edu.sv.education.model.dto.antecedente.AntecedentePatologicoResponseDto;
import ues.edu.sv.education.model.dto.antecedente.AntecedentePatologicoUpdateDto;
import ues.edu.sv.education.model.entity.AntecedentePatologico;
import ues.edu.sv.education.model.entity.Medico;
import ues.edu.sv.education.model.entity.Paciente;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.repository.AntecedentePatologicoRepository;
import ues.edu.sv.education.repository.PacienteRepository;
import ues.edu.sv.education.service.auth.MedicoAutenticado;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HU-12 · Antecedentes patologicos del expediente.
 *
 * El medico que registra sale del token (MedicoAutenticado), nunca del cuerpo:
 * misma defensa que ConsultaService. Un ADMIN que no ejerce no tiene fila en
 * `medicos` y recibe 403 aqui aunque el controlador lo dejara pasar; hoy el
 * controlador ya lo deja fuera, y esta es la segunda cerradura.
 */
@Service
@RequiredArgsConstructor
public class AntecedentePatologicoService {

    private final AntecedentePatologicoRepository antecedenteRepository;
    private final PacienteRepository pacienteRepository;
    private final MedicoAutenticado medicoAutenticado;

    @Transactional
    public AntecedentePatologicoResponseDto crear(AntecedentePatologicoRequestDto request) {
        Medico medico = medicoAutenticado.exigir();
        Paciente paciente = exigirPaciente(request.pacienteId());

        AntecedentePatologico antecedente = AntecedentePatologico.builder()
                .paciente(paciente)
                .tipo(request.tipo())
                .descripcion(request.descripcion().trim())
                .fecha(request.fecha())
                .estado(request.estado())
                .medico(medico)
                .registradoEn(LocalDateTime.now())
                .build();

        return toDto(antecedenteRepository.save(antecedente));
    }

    /**
     * Del mas reciente al mas antiguo (criterio 2). Un paciente sin
     * antecedentes devuelve una lista vacia, no un error (criterio 4); un
     * paciente que no existe, 404: ahi el paciente es el recurso que se pide.
     */
    @Transactional(readOnly = true)
    public List<AntecedentePatologicoResponseDto> listarDelPaciente(Long pacienteId) {
        exigirPaciente(pacienteId);
        return antecedenteRepository
                .findByPaciente_PersonaIdOrderByFechaDescAntecedenteIdDesc(pacienteId)
                .stream()
                .map(AntecedentePatologicoService::toDto)
                .toList();
    }

    @Transactional
    public AntecedentePatologicoResponseDto actualizar(Long antecedenteId, AntecedentePatologicoUpdateDto request) {
        medicoAutenticado.exigir();
        AntecedentePatologico antecedente = exigirAntecedente(antecedenteId);

        antecedente.setTipo(request.tipo());
        antecedente.setDescripcion(request.descripcion().trim());
        antecedente.setFecha(request.fecha());
        antecedente.setEstado(request.estado());

        return toDto(antecedente);
    }

    @Transactional
    public void eliminar(Long antecedenteId) {
        medicoAutenticado.exigir();
        antecedenteRepository.delete(exigirAntecedente(antecedenteId));
    }

    private Paciente exigirPaciente(Long pacienteId) {
        return pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontro el paciente con id: " + pacienteId, "404"));
    }

    private AntecedentePatologico exigirAntecedente(Long antecedenteId) {
        return antecedenteRepository.findById(antecedenteId)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontro el antecedente con id: " + antecedenteId, "404"));
    }

    private static AntecedentePatologicoResponseDto toDto(AntecedentePatologico a) {
        Persona medico = a.getMedico().getPersona();
        return new AntecedentePatologicoResponseDto(
                a.getAntecedenteId(),
                a.getPaciente().getPersonaId(),
                a.getTipo(),
                a.getDescripcion(),
                a.getFecha(),
                a.getEstado(),
                medico.getNombres() + " " + medico.getApellidos(),
                a.getRegistradoEn()
        );
    }
}
