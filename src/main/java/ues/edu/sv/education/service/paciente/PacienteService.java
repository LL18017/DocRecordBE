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
                .expediente(generarExpediente())
                .tipoSangre(request.tipoSangre())
                .creadoEn(LocalDateTime.now())
                // Explicito y no apoyado en el DEFAULT de la columna: con
                // ddl-auto=validate Hibernate manda el INSERT con todas las
                // columnas, asi que un campo null aqui llegaria como null y
                // chocaria contra el NOT NULL en vez de tomar el default.
                .estado(Paciente.ESTADO_ACTIVO)
                .build();

        pacienteRepository.save(paciente);

        return toDto(paciente);
    }

    @Transactional(readOnly = true)
    public java.util.List<PacienteResponseDto> buscar(String texto) {
        // Nunca null contra la consulta: un parametro null ligado en varios
        // puntos del mismo WHERE impide a Postgres inferir su tipo, el driver
        // lo manda como bytea y revienta con "function lower(bytea) does not
        // exist". Para "sin filtro" se pasa "" -- LIKE '%%' coincide con
        // cualquier valor no nulo.
        String filtro = texto == null ? "" : texto;

        java.util.List<Long> ids = pacienteRepository.idsQueCoinciden(filtro);

        // `IN ()` con la lista vacia no es SQL valido, asi que la busqueda sin
        // resultados se corta aqui en vez de llegar a la base.
        if (ids.isEmpty()) return java.util.List.of();

        return pacienteRepository.conPersona(ids).stream().map(this::toDto).toList();
    }

    /**
     * Da de alta o de baja a un paciente.
     *
     * No borra nada: un expediente clinico no se borra, y sus consultas,
     * constantes y recetas siguen existiendo despues de la baja. Lo unico que
     * cambia es que deja de aparecer en los listados de trabajo diario.
     *
     * El valor se valida contra los dos que admite la columna (ver el CHECK de
     * V12). Sin esta comprobacion el error saldria de la base como un 500 sin
     * explicacion, en vez de un 400 que nombra el valor aceptado.
     */
    @Transactional
    public PacienteResponseDto cambiarEstado(Long personaId, String estado) {
        String limpio = estado == null ? "" : estado.trim().toUpperCase();

        if (!Paciente.ESTADO_ACTIVO.equals(limpio) && !Paciente.ESTADO_INACTIVO.equals(limpio)) {
            throw new GeneralException(
                    "Estado no valido: se espera ACTIVO o INACTIVO", "400");
        }

        Paciente paciente = buscarPacienteOFallar(personaId);
        paciente.setEstado(limpio);
        return toDto(pacienteRepository.save(paciente));
    }

    /**
     * Numero de expediente correlativo, con el formato EXP-000001.
     *
     * Lo genera el sistema y no quien registra: un humano no puede saber cual
     * es el siguiente numero libre, y si lo adivina mal choca contra el UNIQUE
     * de la columna despues de haber llenado todo el formulario.
     */
    private String generarExpediente() {
        Long correlativo = pacienteRepository.siguienteCorrelativoDeExpediente();
        return String.format("EXP-%06d", correlativo);
    }


    @Transactional(readOnly = true)
    public PacienteResponseDto obtener(Long personaId) {
        return toDto(buscarPacienteOFallar(personaId));
    }

    /**
     * Actualiza los datos del paciente y los de su persona.
     *
     * Sigue la misma regla que el alta: completar nunca destruye. Un campo que
     * llegue null se interpreta como "no lo estoy tocando", no como "borralo".
     * Sin eso, un formulario que solo edita el telefono borraria la direccion.
     *
     * El expediente NO se toca: es un correlativo emitido por el sistema y
     * cambiarlo romperia cualquier referencia en papel al mismo expediente.
     */
    @Transactional
    public PacienteResponseDto actualizar(Long personaId, PacienteRequestDto request) {

        Paciente paciente = buscarPacienteOFallar(personaId);
        Persona persona = paciente.getPersona();
        PersonaRequestDto datos = request.persona();

        if (datos != null) {
            // El DUI es la identidad que sostiene el mecanismo de no duplicar
            // personas. Cambiarlo en silencio convertiria a esta persona en
            // otra, asi que se rechaza en vez de aceptarlo.
            if (!isBlank(datos.dui())
                    && persona.getDui() != null
                    && !datos.dui().equals(persona.getDui())) {
                throw new GeneralException(
                        "El DUI no coincide con el de la persona registrada", "409");
            }
            if (!isBlank(datos.dui()) && persona.getDui() == null) persona.setDui(datos.dui());
            if (!isBlank(datos.nombres())) persona.setNombres(datos.nombres());
            if (!isBlank(datos.apellidos())) persona.setApellidos(datos.apellidos());
            if (datos.fechaNacimiento() != null) persona.setFechaNacimiento(datos.fechaNacimiento());
            if (!isBlank(datos.sexo())) persona.setSexo(datos.sexo());
            if (!isBlank(datos.telefono())) persona.setTelefono(datos.telefono());
            if (!isBlank(datos.direccion())) persona.setDireccion(datos.direccion());
            if (!isBlank(datos.email())) persona.setEmail(datos.email());
        }

        if (!isBlank(request.tipoSangre())) paciente.setTipoSangre(request.tipoSangre());

        personaRepository.save(persona);
        pacienteRepository.save(paciente);

        return toDto(paciente);
    }

    /**
     * Da de baja al paciente.
     *
     * Se borra SOLO la fila de `pacientes`, nunca la persona: esa misma
     * identidad puede ser ademas medico o enfermera del sistema, y borrarla
     * arrastraria sus otros papeles. Dejar de ser paciente no es dejar de
     * existir.
     */
    @Transactional
    public void eliminar(Long personaId) {
        pacienteRepository.delete(buscarPacienteOFallar(personaId));
    }

    private Paciente buscarPacienteOFallar(Long personaId) {
        return pacienteRepository.findById(personaId)
                .orElseThrow(() -> new NoResourceFoundException("Paciente no encontrado", "404"));
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
                    .email(request.email())
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
        if (request.email() != null) persona.setEmail(request.email());

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
                paciente.getEstado(),
                new PersonaResponseDto(
                        persona.getPersonaId(),
                        persona.getDui(),
                        persona.getNombres(),
                        persona.getApellidos(),
                        persona.getFechaNacimiento(),
                        persona.getSexo(),
                        persona.getTelefono(),
                        persona.getDireccion(),
                        persona.getEmail()
                )
        );
    }
}
