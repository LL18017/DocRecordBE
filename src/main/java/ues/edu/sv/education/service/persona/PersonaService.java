package ues.edu.sv.education.service.persona;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.persona.PersonaConRolesResponseDto;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.repository.EnfermeraRepository;
import ues.edu.sv.education.repository.MedicoRepository;
import ues.edu.sv.education.repository.PacienteRepository;
import ues.edu.sv.education.repository.PersonaRepository;

@Service
@RequiredArgsConstructor
public class PersonaService {

    private final PersonaRepository personaRepository;
    private final MedicoRepository medicoRepository;
    private final EnfermeraRepository enfermeraRepository;
    private final PacienteRepository pacienteRepository;

    // Es lo que permite a la interfaz decir "ya esta registrada como
    // Enfermera, ¿agregarla tambien como Paciente?": sin los tres booleanos
    // el frontend no puede distinguir "no existe" de "existe pero le falta
    // este rol".
    @Transactional(readOnly = true)
    public PersonaConRolesResponseDto buscarPorDui(String dui) {
        Persona persona = personaRepository.findByDui(dui)
                .orElseThrow(() -> new NoResourceFoundException("Persona no encontrada", "404"));
        return toConRolesDto(persona);
    }

    private PersonaConRolesResponseDto toConRolesDto(Persona persona) {
        Long id = persona.getPersonaId();
        return new PersonaConRolesResponseDto(
                persona.getPersonaId(),
                persona.getDui(),
                persona.getNombres(),
                persona.getApellidos(),
                persona.getFechaNacimiento(),
                persona.getSexo(),
                persona.getTelefono(),
                persona.getDireccion(),
                persona.getEmail(),
                medicoRepository.existsById(id),
                enfermeraRepository.existsById(id),
                pacienteRepository.existsById(id)
        );
    }
}
