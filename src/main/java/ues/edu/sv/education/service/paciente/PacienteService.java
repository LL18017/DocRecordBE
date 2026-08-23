package ues.edu.sv.education.service.paciente;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.paciente.PacienteRequestDto;
import ues.edu.sv.education.model.dto.paciente.PacienteResponseDto;
import ues.edu.sv.education.model.dto.persona.PersonaRequestDto;
import ues.edu.sv.education.model.dto.persona.PersonaResponseDto;
import ues.edu.sv.education.model.entity.Paciente;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.repository.PacienteRepository;
import ues.edu.sv.education.repository.PersonaRepository;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PacienteService {

    private final PersonaRepository personaRepository;
    private final PacienteRepository pacienteRepository;

    // La identidad se busca antes de crearse: si la persona ya existe (via
    // personaId) se REUTILIZA -completando los campos que lleguen, sin
    // borrar los que ya tenia-; si no, se crea. Solo despues se inserta la
    // fila de paciente.
    @Transactional
    public PacienteResponseDto crear(PacienteRequestDto request) {

        Persona persona = resolverPersona(request.persona());

        if (persona.getFechaNacimiento() == null || persona.getSexo() == null) {
            throw new GeneralException(
                    "Para registrar un paciente se requiere su fecha de nacimiento y su sexo",
                    "422"
            );
        }

        if (pacienteRepository.existsById(persona.getPersonaId())) {
            throw new GeneralException("Esta persona ya esta registrada como paciente", "409");
        }

        Paciente paciente = Paciente.builder()
                .persona(persona)
                .expediente(request.expediente())
                .tipoSangre(request.tipoSangre())
                .creadoEn(LocalDateTime.now())
                .build();

        pacienteRepository.save(paciente);

        return toDto(paciente);
    }

    @Transactional(readOnly = true)
    public java.util.List<PacienteResponseDto> buscar(String texto) {
        return pacienteRepository.buscar(texto).stream().map(this::toDto).toList();
    }

    private Persona resolverPersona(PersonaRequestDto request) {

        if (request.personaId() == null) {
            if (isBlank(request.nombres()) || isBlank(request.apellidos())) {
                throw new GeneralException(
                        "Nombres y apellidos son obligatorios para registrar una persona nueva",
                        "400"
                );
            }
            return personaRepository.save(Persona.builder()
                    .dui(request.dui())
                    .nombres(request.nombres())
                    .apellidos(request.apellidos())
                    .fechaNacimiento(request.fechaNacimiento())
                    .sexo(request.sexo())
                    .telefono(request.telefono())
                    .direccion(request.direccion())
                    .build());
        }

        Persona persona = personaRepository.findById(request.personaId())
                .orElseThrow(() -> new NoResourceFoundException("Persona no encontrada", "404"));

        // Completar nunca destruye datos: solo se pisa lo que llega no nulo.
        // El DUI es la clave que sostiene la reutilizacion de identidad, asi
        // que un DUI distinto al que ya tiene la persona no se sobreescribe
        // en silencio -es señal de que se estan confundiendo dos personas.
        if (request.dui() != null) {
            if (persona.getDui() != null && !Objects.equals(persona.getDui(), request.dui())) {
                throw new GeneralException(
                        "El DUI recibido no coincide con el de la persona existente",
                        "409"
                );
            }
            persona.setDui(request.dui());
        }
        if (request.nombres() != null) persona.setNombres(request.nombres());
        if (request.apellidos() != null) persona.setApellidos(request.apellidos());
        if (request.fechaNacimiento() != null) persona.setFechaNacimiento(request.fechaNacimiento());
        if (request.sexo() != null) persona.setSexo(request.sexo());
        if (request.telefono() != null) persona.setTelefono(request.telefono());
        if (request.direccion() != null) persona.setDireccion(request.direccion());

        return personaRepository.save(persona);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private PacienteResponseDto toDto(Paciente paciente) {
        Persona persona = paciente.getPersona();
        return new PacienteResponseDto(
                paciente.getPersonaId(),
                paciente.getExpediente(),
                paciente.getTipoSangre(),
                paciente.getCreadoEn(),
                new PersonaResponseDto(
                        persona.getPersonaId(),
                        persona.getDui(),
                        persona.getNombres(),
                        persona.getApellidos(),
                        persona.getFechaNacimiento(),
                        persona.getSexo(),
                        persona.getTelefono(),
                        persona.getDireccion()
                )
        );
    }
}
