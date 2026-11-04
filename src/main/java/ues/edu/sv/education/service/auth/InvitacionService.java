package ues.edu.sv.education.service.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.model.entity.Clinicas;
import ues.edu.sv.education.model.entity.Invitacion;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.enums.RolesEnum;
import ues.edu.sv.education.repository.InvitacionRepository;
import ues.edu.sv.education.repository.RoleRepository;
import ues.edu.sv.education.repository.UserRepository;
import ues.edu.sv.education.service.EmailService;
import ues.edu.sv.education.service.PlantillaDeCorreo;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * La cuenta que se abre sobre una ficha que ya existe (HU-02).
 *
 * ── El modelo ─────────────────────────────────────────────────────────────
 * Primero existe la persona en su papel -la ficha del medico, el expediente
 * del paciente-, y despues, si hace falta, su cuenta para entrar. La cuenta no
 * es el medico: es el acceso del medico. Por eso no la crea la persona sola
 * (se retiro el registro publico) ni se le inventa una contrasena: el
 * administrador la abre y la persona elige la suya con una invitacion.
 *
 * Lo usan la ficha de medico (MedicoService.darAcceso) y el expediente del
 * paciente (AccesoAlPortalService.darAcceso, HU-34): la misma cuenta sobre el
 * expediente, con rol PACIENTE y sin sede. Al paciente se la abre el personal
 * que lo atiende, no solo el administrador; por eso el correo dice "tu
 * clinica" y no "el administrador".
 *
 * ── Como queda la cuenta mientras tanto ───────────────────────────────────
 * enabled = false y una contrasena aleatoria que nadie conoce: no se puede
 * entrar hasta canjear la invitacion. Canjearla pone la contrasena elegida y
 * habilita la cuenta. Habilitar aqui SI es correcto, a diferencia de
 * restablecer la contrasena (ver RecuperacionDeContrasenaService.restablecer):
 * `enabled` solo dice que la cuenta se confirmo, y desactivarla es otro campo
 * (`activo`, UserService.cambiarEstado), que esto no toca.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvitacionService {

    /** Tres dias: lo que tarda alguien ocupado en abrir un correo de trabajo. */
    static final long VIGENCIA_EN_HORAS = 72;

    private static final String RUTA_DEL_FORMULARIO = "/activar";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final InvitacionRepository invitacionRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String urlDelFrontend;

    /** La cuenta creada y si el correo salio. */
    public record Resultado(User usuario, boolean correoEnviado) {
    }

    /**
     * Abre la cuenta de la persona con ese rol y le manda la invitacion.
     *
     * 409 si el correo ya es de otra cuenta. `sede` es la primera clinica
     * donde va a trabajar el personal; null para el paciente.
     */
    @Transactional
    public Resultado invitar(Persona persona, String correo, RolesEnum rol, Clinicas sede) {

        String limpio = correo.trim().toLowerCase();
        if (userRepository.findByEmailIgnoreCase(limpio).isPresent()) {
            throw new GeneralException("Este correo ya está registrado", "409");
        }

        Set<Clinicas> sedes = new HashSet<>();
        if (sede != null) sedes.add(sede);

        User usuario = userRepository.save(User.builder()
                .persona(persona)
                .email(limpio)
                // Nadie la conoce: hasta canjear la invitacion no hay como entrar.
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .enabled(false)
                .roles(new HashSet<>(Set.of(roleRepository.getReferenceById(rol.getId()))))
                .clinicasAsignadas(sedes)
                .build());

        return new Resultado(usuario, enviar(usuario, rol));
    }

    /**
     * Manda una invitacion nueva a una cuenta que todavia no se activo. Las
     * anteriores dejan de servir: solo vale el ultimo correo.
     */
    @Transactional
    public boolean reenviar(User usuario, RolesEnum rol) {
        if (usuario.isEnabled()) {
            throw new GeneralException("La cuenta ya está activada; no hace falta otra invitación", "409");
        }
        quemarPendientes(usuario);
        return enviar(usuario, rol);
    }

    /**
     * Canjea la invitacion: pone la contrasena elegida y habilita la cuenta.
     * Usada, vencida o inexistente dan el mismo error, para no decir cual
     * enlace existio.
     */
    @Transactional
    public void activar(String token, String contrasena) {

        Invitacion invitacion = invitacionRepository.findByToken(token)
                .filter(Invitacion::sirve)
                .orElseThrow(() -> new GeneralException(
                        "El enlace de invitación no es válido o ya venció. Pida al administrador uno nuevo.",
                        "400"));

        User usuario = invitacion.getUsuario();
        usuario.setPassword(passwordEncoder.encode(contrasena));
        usuario.setEnabled(true);
        userRepository.save(usuario);

        quemarPendientes(usuario);
        log.info("Cuenta {} activada por invitacion.", usuario.getUserID());
    }

    private void quemarPendientes(User usuario) {
        List<Invitacion> pendientes = invitacionRepository.findByUsuarioAndUsadaFalse(usuario);
        pendientes.forEach(p -> p.setUsada(true));
        invitacionRepository.saveAll(pendientes);
    }

    /**
     * Guarda el enlace y manda el correo. Si el correo falla, la invitacion
     * queda guardada y se puede reenviar: el dato es la cuenta, el correo es
     * una notificacion (mismo criterio que UserService.createUser).
     */
    private boolean enviar(User usuario, RolesEnum rol) {
        String token = UUID.randomUUID().toString();
        LocalDateTime ahora = LocalDateTime.now();
        invitacionRepository.save(Invitacion.builder()
                .token(token)
                .usuario(usuario)
                .venceEn(ahora.plusHours(VIGENCIA_EN_HORAS))
                .usada(false)
                .creadaEn(ahora)
                .build());

        String enlace = UriComponentsBuilder.fromUriString(urlDelFrontend)
                .path(RUTA_DEL_FORMULARIO)
                .queryParam("token", token)
                .build()
                .toUriString();

        Map<String, Object> datos = new HashMap<>();
        datos.put("nombre", usuario.getPersona().getNombres());
        datos.put("rol", etiquetaDe(rol));
        datos.put("enlace", enlace);
        datos.put("horas", VIGENCIA_EN_HORAS);

        try {
            emailService.enviarCorreo(usuario.getEmail(), PlantillaDeCorreo.INVITACION, datos);
            return true;
        } catch (MailException e) {
            log.error("No se pudo enviar la invitacion a {}. Quedo guardada; el administrador puede "
                    + "reenviarla.", usuario.getEmail(), e);
            return false;
        }
    }

    private static String etiquetaDe(RolesEnum rol) {
        return switch (rol) {
            case MEDICO -> "médico";
            case ENFERMERA -> "enfermera";
            case PACIENTE -> "paciente";
            case ADMIN -> "administrador";
        };
    }
}
