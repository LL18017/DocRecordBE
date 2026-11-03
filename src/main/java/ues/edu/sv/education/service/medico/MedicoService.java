package ues.edu.sv.education.service.medico;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.medico.AccesoRequestDto;
import ues.edu.sv.education.model.dto.medico.AccesoResponseDto;
import ues.edu.sv.education.model.dto.medico.MedicoRequestDto;
import ues.edu.sv.education.model.dto.medico.MedicoResponseDto;
import ues.edu.sv.education.model.entity.*;
import ues.edu.sv.education.model.enums.RolesEnum;
import ues.edu.sv.education.repository.ClinicaRepository;
import ues.edu.sv.education.repository.EspecialidadRepository;
import ues.edu.sv.education.repository.MedicoRepository;
import ues.edu.sv.education.repository.PersonaRepository;
import ues.edu.sv.education.repository.RoleRepository;
import ues.edu.sv.education.repository.UserRepository;
import ues.edu.sv.education.service.auth.AdminAutenticado;
import ues.edu.sv.education.service.auth.InvitacionService;
import ues.edu.sv.education.service.user.BitacoraDeUsuarios;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Fichas de medico y su acceso al sistema (HU-02).
 *
 * ── Ficha primero, cuenta despues ─────────────────────────────────────────
 * Igual que el paciente: primero existe en su papel y despues, si hace falta,
 * la cuenta para entrar. La ficha (tabla `medicos`, colgada de `persona`)
 * lleva lo que hace medico a alguien -especialidad y numero de junta-; la
 * cuenta (`users`) solo dice como entra. Antes la ficha se creaba como efecto
 * de dar el rol MEDICO en Usuarios y Roles, mezclando el acceso con los datos
 * profesionales; ahora Usuarios y Roles ya no la crea (UserService.addRole).
 *
 * ── Baja ──────────────────────────────────────────────────────────────────
 * Dar de baja la ficha la marca inactiva y le quita a la cuenta el rol
 * MEDICO: deja de atender. No borra nada; sus consultas siguen firmadas por
 * el, con su junta. Reactivarla no devuelve el rol solo: se le vuelve a dar
 * acceso desde la ficha, que es una decision aparte.
 */
@Service
@RequiredArgsConstructor
public class MedicoService {

    private final MedicoRepository medicoRepository;
    private final PersonaRepository personaRepository;
    private final EspecialidadRepository especialidadRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ClinicaRepository clinicaRepository;
    private final InvitacionService invitacionService;
    private final AdminAutenticado adminAutenticado;
    private final BitacoraDeUsuarios bitacora;

    @Transactional(readOnly = true)
    public List<MedicoResponseDto> listar(Boolean activo) {
        return medicoRepository.findAll().stream()
                .filter(m -> activo == null || m.isActivo() == activo)
                .sorted(Comparator.comparing((Medico m) -> m.getPersona().getApellidos(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(m -> m.getPersona().getNombres(), String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public MedicoResponseDto obtener(Long personaId) {
        return toDto(fichaOFallar(personaId));
    }

    /**
     * Registra la ficha. Sin cuenta: la cuenta se da despues con darAcceso.
     *
     * Con userId, la ficha se cuelga de la persona de esa cuenta -el
     * administrador que tambien ejerce-, en vez de crear una persona repetida.
     */
    @Transactional
    public MedicoResponseDto crear(MedicoRequestDto request) {

        Especialidad especialidad = especialidadActiva(request.especialidadId());

        Persona persona;
        if (request.userId() != null) {
            User cuenta = userRepository.findById(request.userId())
                    .orElseThrow(() -> new NoResourceFoundException("No se encontro el usuario con id: "
                            + request.userId(), "404"));
            persona = cuenta.getPersona();
            if (medicoRepository.existsById(persona.getPersonaId())) {
                throw new GeneralException("Esta persona ya tiene ficha de médico", "409");
            }
        } else {
            persona = null;
        }

        String junta = juntaLibre(request.registroJunta(), persona == null ? null : persona.getPersonaId());

        if (persona == null) {
            persona = personaRepository.save(Persona.builder()
                    .nombres(request.nombres().trim())
                    .apellidos(request.apellidos().trim())
                    .telefono(telefonoNormalizado(request.telefono()))
                    .build());
        } else if (persona.getTelefono() == null && telefonoNormalizado(request.telefono()) != null) {
            persona.setTelefono(telefonoNormalizado(request.telefono()));
            personaRepository.save(persona);
        }

        // Ojo: no fijar personaId a mano. Con @MapsId lo deriva de la persona;
        // con el id puesto, Spring Data haria merge en vez de persist.
        Medico ficha = medicoRepository.save(Medico.builder()
                .persona(persona)
                .especialidad(especialidad)
                .registroJunta(junta)
                .activo(true)
                .build());
        return toDto(ficha);
    }

    /** Corrige la ficha: datos personales, especialidad y junta. */
    @Transactional
    public MedicoResponseDto actualizar(Long personaId, MedicoRequestDto request) {

        Medico ficha = fichaOFallar(personaId);
        Persona persona = ficha.getPersona();

        persona.setNombres(request.nombres().trim());
        persona.setApellidos(request.apellidos().trim());
        persona.setTelefono(telefonoNormalizado(request.telefono()));
        personaRepository.save(persona);

        ficha.setEspecialidad(especialidadActiva(request.especialidadId()));
        ficha.setRegistroJunta(juntaLibre(request.registroJunta(), personaId));
        return toDto(medicoRepository.save(ficha));
    }

    /**
     * Baja o reactivacion de la ficha. La baja le quita el rol MEDICO a la
     * cuenta, si la tiene; reactivar no se lo devuelve (ver la cabecera).
     */
    @Transactional
    public MedicoResponseDto cambiarEstado(Long personaId, boolean activo) {

        User administrador = adminAutenticado.exigir();
        Medico ficha = fichaOFallar(personaId);
        ficha.setActivo(activo);
        medicoRepository.save(ficha);

        if (!activo) {
            cuentaDe(personaId).ifPresent(cuenta -> {
                boolean quitado = cuenta.getRoles().removeIf(r -> RolesEnum.MEDICO.getName().equals(r.getName()));
                if (quitado) {
                    userRepository.save(cuenta);
                    bitacora.registrar(cuenta.getUserID(), BitacoraDeUsuario.ROL_RETIRADO,
                            "Rol MEDICO (baja de la ficha de médico)", administrador.getEmail());
                }
            });
        }
        return toDto(ficha);
    }

    /**
     * Le da acceso al sistema al medico de la ficha.
     *
     * Sin cuenta: la crea con el correo indicado, el rol MEDICO y la sede, y
     * manda la invitacion para que elija su contrasena (InvitacionService).
     * Con cuenta: le da el rol MEDICO si no lo tenia y la sede si viene; si la
     * cuenta todavia no se activo, manda una invitacion nueva. Es idempotente:
     * pedirlo dos veces no duplica nada.
     */
    @Transactional
    public AccesoResponseDto darAcceso(Long personaId, AccesoRequestDto request) {

        User administrador = adminAutenticado.exigir();
        Medico ficha = fichaOFallar(personaId);
        if (!ficha.isActivo()) {
            throw new GeneralException("La ficha está dada de baja: reactívela antes de darle acceso", "409");
        }

        Clinicas sede = request.clinicaId() == null
                ? null
                : clinicaRepository.findById(request.clinicaId())
                        .orElseThrow(() -> new NoResourceFoundException(
                                "No se encontro la clinica con id: " + request.clinicaId(), "404"));

        Optional<User> existente = cuentaDe(personaId);

        if (existente.isEmpty()) {
            if (request.email() == null || request.email().isBlank()) {
                throw new GeneralException("Indique el correo con que el médico va a iniciar sesión", "400");
            }
            if (sede == null) {
                throw new GeneralException(
                        "Indique la sede donde va a trabajar: sin ninguna no puede entrar", "400");
            }
            InvitacionService.Resultado r = invitacionService.invitar(
                    ficha.getPersona(), request.email(), RolesEnum.MEDICO, sede);
            bitacora.registrar(r.usuario().getUserID(), BitacoraDeUsuario.ALTA,
                    "Cuenta de médico creada desde su ficha; invitación enviada", administrador.getEmail());
            return new AccesoResponseDto(toDto(ficha), true, r.correoEnviado());
        }

        User cuenta = existente.get();
        boolean tieneRol = cuenta.getRoles().stream().anyMatch(r -> RolesEnum.MEDICO.getName().equals(r.getName()));
        if (!tieneRol) {
            cuenta.getRoles().add(roleRepository.getReferenceById(RolesEnum.MEDICO.getId()));
            bitacora.registrar(cuenta.getUserID(), BitacoraDeUsuario.ROL_ASIGNADO,
                    "Rol MEDICO (acceso desde la ficha de médico)", administrador.getEmail());
        }
        if (cuenta.getClinicasAsignadas() == null) cuenta.setClinicasAsignadas(new HashSet<>());
        if (sede == null && cuenta.getClinicasAsignadas().isEmpty()) {
            throw new GeneralException(
                    "Indique la sede donde va a trabajar: sin ninguna no puede entrar", "400");
        }
        if (sede != null && cuenta.getClinicasAsignadas().stream()
                .noneMatch(c -> Objects.equals(c.getClinicaId(), sede.getClinicaId()))) {
            cuenta.getClinicasAsignadas().add(sede);
        }
        userRepository.save(cuenta);

        if (!cuenta.isEnabled()) {
            boolean enviado = invitacionService.reenviar(cuenta, RolesEnum.MEDICO);
            return new AccesoResponseDto(toDto(ficha), true, enviado);
        }
        return new AccesoResponseDto(toDto(ficha), false, false);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    private Medico fichaOFallar(Long personaId) {
        return medicoRepository.findById(personaId)
                .orElseThrow(() -> new NoResourceFoundException("Ficha de médico no encontrada", "404"));
    }

    private Optional<User> cuentaDe(Long personaId) {
        return userRepository.findByPersona_PersonaId(personaId);
    }

    private Especialidad especialidadActiva(Long especialidadId) {
        Especialidad especialidad = especialidadRepository.findById(especialidadId)
                .orElseThrow(() -> new NoResourceFoundException("Especialidad no encontrada", "404"));
        if (!especialidad.isActiva()) {
            throw new GeneralException("La especialidad está retirada del catálogo", "400");
        }
        return especialidad;
    }

    /**
     * La junta normalizada (sin espacios alrededor, en mayusculas), o 409 si
     * ya es de otro medico. El formato ya lo valido el DTO.
     */
    private String juntaLibre(String registroJunta, Long personaPropia) {
        String junta = registroJunta.trim().toUpperCase();
        medicoRepository.findByRegistroJuntaIgnoreCase(junta)
                .filter(otro -> !otro.getPersonaId().equals(personaPropia))
                .ifPresent(otro -> {
                    throw new GeneralException("Este número de junta de vigilancia ya está registrado", "409");
                });
        return junta;
    }

    /** "22222222" y "2222-2222" son el mismo numero: se guarda con guion. */
    private static String telefonoNormalizado(String telefono) {
        if (telefono == null || telefono.isBlank()) return null;
        String digitos = telefono.replace("-", "").trim();
        return digitos.substring(0, 4) + "-" + digitos.substring(4);
    }

    private MedicoResponseDto toDto(Medico ficha) {
        Persona persona = ficha.getPersona();
        Especialidad especialidad = ficha.getEspecialidad();

        MedicoResponseDto.CuentaDto cuenta = cuentaDe(ficha.getPersonaId())
                .map(u -> new MedicoResponseDto.CuentaDto(
                        u.getUserID(),
                        u.getEmail(),
                        !u.isEnabled() ? "SIN_ACTIVAR" : (u.isActivo() ? "ACTIVA" : "DESACTIVADA"),
                        u.getRoles().stream().anyMatch(r -> RolesEnum.MEDICO.getName().equals(r.getName())),
                        u.getClinicasAsignadas() == null ? List.of() : u.getClinicasAsignadas().stream()
                                .sorted(Comparator.comparing(Clinicas::getName, String.CASE_INSENSITIVE_ORDER))
                                .map(c -> new MedicoResponseDto.SedeDto(c.getClinicaId(), c.getName()))
                                .toList()))
                .orElse(null);

        return new MedicoResponseDto(
                ficha.getPersonaId(),
                persona.getNombres(),
                persona.getApellidos(),
                persona.getTelefono(),
                especialidad.getEspecialidadId(),
                especialidad.getNombre(),
                ficha.getRegistroJunta(),
                ficha.isActivo(),
                cuenta);
    }
}
