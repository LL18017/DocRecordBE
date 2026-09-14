package ues.edu.sv.education.service.enfermera;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.enfermera.EnfermeraRequestDto;
import ues.edu.sv.education.model.dto.enfermera.EnfermeraResponseDto;
import ues.edu.sv.education.model.dto.persona.PersonaRequestDto;
import ues.edu.sv.education.model.dto.persona.PersonaResponseDto;
import ues.edu.sv.education.model.entity.Enfermera;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.Role;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.enums.RolesEnum;
import ues.edu.sv.education.repository.EnfermeraRepository;
import ues.edu.sv.education.repository.PersonaRepository;
import ues.edu.sv.education.repository.RoleRepository;
import ues.edu.sv.education.repository.UserRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Alta y consulta del personal de enfermeria.
 *
 * El alta crea TRES cosas de una vez -la persona, la fila en `enfermeras` y la
 * cuenta con rol ENFERMERA- dentro de una sola transaccion. Partirlo en
 * llamadas sueltas dejaria estados imposibles de diagnosticar: un usuario con
 * rol ENFERMERA pero sin fila en `enfermeras` pasa el hasRole del controlador
 * de signos vitales y despues recibe un 403 del servicio, y quien lo sufre no
 * tiene forma de saber que le falta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnfermeraService {

    private final EnfermeraRepository enfermeraRepository;
    private final PersonaRepository personaRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public EnfermeraResponseDto crear(EnfermeraRequestDto request) {

        // El correo de acceso se comprueba ANTES de tocar nada. users.email es
        // UNIQUE, asi que sin esta guarda el choque saldria como una violacion
        // de restriccion (un 500) despues de haber creado ya la persona.
        if (userRepository.findByEmailIgnoreCase(request.emailDeAcceso()).isPresent()) {
            throw new GeneralException("Ya existe una cuenta con ese correo de acceso", "409");
        }

        Persona persona = resolverPersona(request.persona());

        if (enfermeraRepository.existsById(persona.getPersonaId())) {
            throw new GeneralException("Esta persona ya esta registrada como enfermera", "409");
        }

        Enfermera enfermera = enfermeraRepository.save(Enfermera.builder()
                .persona(persona)
                .registroJunta(request.registroJunta())
                .activo(true)
                .build());

        Role rolEnfermera = roleRepository.getReferenceById(RolesEnum.ENFERMERA.getId());

        // enabled = true, a diferencia de /auth/register, que deja la cuenta
        // apagada hasta que llega el correo de confirmacion. Aqui el alta la
        // hace un administrador que ya respondio por esta persona: exigirle
        // ademas confirmar un correo no comprueba nada nuevo y deja a la
        // enfermera sin poder entrar si el SMTP falla. Mismo criterio que
        // AdminBootstrap con el primer administrador y que
        // UserService.asignarContrasena, que tambien habilita.
        User cuenta = userRepository.save(User.builder()
                .persona(persona)
                .email(request.emailDeAcceso())
                .password(passwordEncoder.encode(request.password()))
                .enabled(true)
                .roles(new HashSet<>(Set.of(rolEnfermera)))
                .build());

        // Nunca la contrasena en el log, ni en claro ni cifrada.
        log.info("Alta de enfermeria: persona {} con cuenta {}",
                persona.getPersonaId(), request.emailDeAcceso());

        return toDto(enfermera, cuenta);
    }

    @Transactional(readOnly = true)
    public List<EnfermeraResponseDto> listar() {
        return enfermeraRepository.findAll().stream()
                .map(enfermera -> toDto(enfermera, cuentaDe(enfermera)))
                .toList();
    }

    @Transactional(readOnly = true)
    public EnfermeraResponseDto obtener(Long personaId) {
        Enfermera enfermera = enfermeraRepository.findById(personaId)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontro la enfermera con id: " + personaId, "404"));
        return toDto(enfermera, cuentaDe(enfermera));
    }

    /**
     * Da de baja a una enfermera sin borrarla.
     *
     * `activo = false` y no DELETE: la fila la referencian las tomas de signos
     * vitales que hizo (FK con ON DELETE RESTRICT en V9), y borrarla dejaria
     * constantes sin responsable. Una baja laboral no puede reescribir el
     * historial clinico.
     */
    @Transactional
    public EnfermeraResponseDto darDeBaja(Long personaId) {
        Enfermera enfermera = enfermeraRepository.findById(personaId)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontro la enfermera con id: " + personaId, "404"));

        enfermera.setActivo(false);
        enfermeraRepository.save(enfermera);

        // La cuenta se apaga tambien: dejarla encendida permitiria seguir
        // registrando constantes a alguien que ya no trabaja aqui.
        User cuenta = cuentaDe(enfermera);
        if (cuenta != null) {
            // `activo` y no `enabled`: su correo sigue confirmado -- eso no se
            // deshace --; lo que cambia es que la organizacion ya no le
            // permite entrar. Ver V15.
            cuenta.setActivo(false);
            userRepository.save(cuenta);
        }

        return toDto(enfermera, cuenta);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    /**
     * La cuenta de acceso de una enfermera, o null si no tiene.
     *
     * Puede ser null de verdad: nada obliga a que toda fila de `enfermeras`
     * tenga usuario -las que existieran antes de este endpoint, o una insertada
     * a mano-. Se devuelve null en vez de fallar para que el listado siga
     * sirviendo y la falta se vea, que es justo lo que un administrador
     * necesita para arreglarla.
     */
    private User cuentaDe(Enfermera enfermera) {
        return userRepository.findByPersona_PersonaId(enfermera.getPersonaId()).orElse(null);
    }

    /** Mismo criterio que PacienteService.resolverPersona: completar nunca destruye. */
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
                    .email(request.email())
                    .build());
        }

        Persona persona = personaRepository.findById(request.personaId())
                .orElseThrow(() -> new NoResourceFoundException("Persona no encontrada", "404"));

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
        if (request.email() != null) persona.setEmail(request.email());

        return personaRepository.save(persona);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private EnfermeraResponseDto toDto(Enfermera enfermera, User cuenta) {
        Persona persona = enfermera.getPersona();
        return new EnfermeraResponseDto(
                enfermera.getPersonaId(),
                new PersonaResponseDto(
                        persona.getPersonaId(),
                        persona.getDui(),
                        persona.getNombres(),
                        persona.getApellidos(),
                        persona.getFechaNacimiento(),
                        persona.getSexo(),
                        persona.getTelefono(),
                        persona.getDireccion(),
                        persona.getEmail()),
                enfermera.getRegistroJunta(),
                enfermera.isActivo(),
                cuenta == null ? null : cuenta.getUserID(),
                cuenta == null ? null : cuenta.getEmail());
    }
}
