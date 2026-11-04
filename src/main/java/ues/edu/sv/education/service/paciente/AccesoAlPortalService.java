package ues.edu.sv.education.service.paciente;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.paciente.AccesoAlPortalDto;
import ues.edu.sv.education.model.dto.paciente.AccesoAlPortalRequestDto;
import ues.edu.sv.education.model.dto.portal.AccesoAlExpedienteDto;
import ues.edu.sv.education.model.entity.BitacoraDeUsuario;
import ues.edu.sv.education.model.entity.Paciente;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.enums.RolesEnum;
import ues.edu.sv.education.repository.PacienteRepository;
import ues.edu.sv.education.repository.RoleRepository;
import ues.edu.sv.education.repository.UserRepository;
import ues.edu.sv.education.service.auth.InvitacionService;
import ues.edu.sv.education.service.auth.UsuarioAutenticado;
import ues.edu.sv.education.service.portal.BitacoraDeAccesos;
import ues.edu.sv.education.service.user.BitacoraDeUsuarios;

import java.util.List;
import java.util.Optional;

/**
 * La cuenta del paciente para su portal (HU-34), abierta desde su expediente.
 *
 * Es MedicoService.darAcceso del lado del paciente, con el mismo modelo de
 * HU-02: primero existe el expediente y despues, si el paciente lo quiere, la
 * cuenta para entrar a verlo. La cuenta la abre el personal y la persona elige
 * su contrasena con una invitacion (InvitacionService); nadie le inventa una.
 *
 * ── Quien puede darlo ─────────────────────────────────────────────────────
 * Los mismos que registran pacientes (ADMIN, MEDICO, ENFERMERA): abrir la
 * cuenta del portal es parte de recibir al paciente en la clinica, no una
 * decision administrativa como dar de baja. Y no hay escalada posible: el rol
 * es siempre PACIENTE y la cuenta queda colgada de la persona de ESE
 * expediente, que es lo unico que el rol deja ver.
 *
 * ── A diferencia del medico ───────────────────────────────────────────────
 * Sin sede: el paciente no opera en ninguna clinica. Y un paciente dado de
 * baja SI puede recibir acceso: la baja lo saca de los listados de trabajo,
 * pero su expediente sigue siendo suyo y leerlo sigue permitido (HU-10). La
 * baja del medico es otra cosa -deja de atender-, por eso alli es 409.
 */
@Service
@RequiredArgsConstructor
public class AccesoAlPortalService {

    private final PacienteRepository pacienteRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final InvitacionService invitacionService;
    private final UsuarioAutenticado usuarioAutenticado;
    private final BitacoraDeUsuarios bitacoraDeUsuarios;
    private final BitacoraDeAccesos bitacoraDeAccesos;

    /** Como esta hoy el acceso del paciente. 404 si el paciente no existe. */
    @Transactional(readOnly = true)
    public AccesoAlPortalDto consultar(Long personaId) {
        Paciente paciente = pacienteOFallar(personaId);
        return dto(paciente, cuentaDe(personaId).orElse(null), false, false);
    }

    /**
     * Le da al paciente acceso a su portal.
     *
     * Sin cuenta: la crea con el correo indicado -o, si no viene, el de
     * contacto de su ficha- y el rol PACIENTE, y manda la invitacion. Con
     * cuenta sin activar: manda una invitacion nueva (las anteriores dejan de
     * servir). Con cuenta activa: le agrega el rol PACIENTE si no lo tenia, que
     * es el caso del medico o la enfermera que tambien se atiende aqui. Es
     * idempotente: pedirlo dos veces no duplica nada.
     */
    @Transactional
    public AccesoAlPortalDto darAcceso(Long personaId, AccesoAlPortalRequestDto request) {

        User quienOpera = usuarioAutenticado.exigir();
        Paciente paciente = pacienteOFallar(personaId);
        Optional<User> existente = cuentaDe(personaId);

        if (existente.isEmpty()) {
            String correo = request == null || request.email() == null || request.email().isBlank()
                    ? paciente.getPersona().getEmail()
                    : request.email();
            if (correo == null || correo.isBlank()) {
                throw new GeneralException(
                        "Indique el correo con que el paciente va a iniciar sesión en su portal", "400");
            }
            InvitacionService.Resultado r = invitacionService.invitar(
                    paciente.getPersona(), correo, RolesEnum.PACIENTE, null);
            bitacoraDeUsuarios.registrar(r.usuario().getUserID(), BitacoraDeUsuario.ALTA,
                    "Cuenta del portal del paciente creada desde su expediente; invitación enviada",
                    quienOpera.getEmail());
            return dto(paciente, r.usuario(), true, r.correoEnviado());
        }

        User cuenta = existente.get();
        if (!tieneRolPaciente(cuenta)) {
            cuenta.getRoles().add(roleRepository.getReferenceById(RolesEnum.PACIENTE.getId()));
            userRepository.save(cuenta);
            bitacoraDeUsuarios.registrar(cuenta.getUserID(), BitacoraDeUsuario.ROL_ASIGNADO,
                    "Rol PACIENTE (acceso al portal desde su expediente)", quienOpera.getEmail());
        }

        if (!cuenta.isEnabled()) {
            boolean enviado = invitacionService.reenviar(cuenta, RolesEnum.PACIENTE);
            return dto(paciente, cuenta, true, enviado);
        }
        return dto(paciente, cuenta, false, false);
    }

    /**
     * Quien abrio o descargo el expediente desde el portal, lo mas reciente
     * primero (HU-34 criterio 6). 404 si el paciente no existe: una lista
     * vacia diria "nadie lo ha abierto", que de un paciente inexistente es
     * una afirmacion sin sentido.
     */
    @Transactional(readOnly = true)
    public List<AccesoAlExpedienteDto> accesosAlExpediente(Long personaId) {
        pacienteOFallar(personaId);
        return bitacoraDeAccesos.dePaciente(personaId);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    private Paciente pacienteOFallar(Long personaId) {
        return pacienteRepository.findById(personaId)
                .orElseThrow(() -> new NoResourceFoundException("Paciente no encontrado", "404"));
    }

    private Optional<User> cuentaDe(Long personaId) {
        return userRepository.findByPersona_PersonaId(personaId);
    }

    private static boolean tieneRolPaciente(User cuenta) {
        return cuenta.getRoles().stream().anyMatch(r -> RolesEnum.PACIENTE.getName().equals(r.getName()));
    }

    private static AccesoAlPortalDto dto(Paciente paciente, User cuenta, boolean invitacionEnviada,
                                         boolean correoEnviado) {
        AccesoAlPortalDto.CuentaDto cuentaDto = cuenta == null ? null : new AccesoAlPortalDto.CuentaDto(
                cuenta.getUserID(),
                cuenta.getEmail(),
                // Mismo criterio que la ficha del medico: sin activar pesa mas
                // que desactivada, porque es lo primero que hay que resolver.
                !cuenta.isEnabled() ? "SIN_ACTIVAR" : (cuenta.isActivo() ? "ACTIVA" : "DESACTIVADA"),
                tieneRolPaciente(cuenta));
        return new AccesoAlPortalDto(
                paciente.getPersonaId(),
                paciente.getPersona().getEmail(),
                cuentaDto,
                invitacionEnviada,
                correoEnviado);
    }
}
