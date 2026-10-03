package ues.edu.sv.education.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.enums.RolesEnum;
import ues.edu.sv.education.repository.UserRepository;

/**
 * Resuelve QUIEN esta operando y confirma que tiene ROLE_ADMIN, a partir del
 * token y solo del token -- misma idea que MedicoAutenticado, para el rol
 * administrativo.
 *
 * Por que hace falta pudiendo bastar con @PreAuthorize("hasRole('ADMIN')") en
 * el controller: esa anotacion protege el borde HTTP, no el servicio. Es el
 * mismo criterio que ya usa ConsultaService con MedicoAutenticado -la
 * anotacion es la primera capa, el servicio es la que tiene dientes de
 * verdad-, y aqui hace falta ademas por una segunda razon: UserService.
 * asignarContrasena necesita saber QUIEN es el administrador que esta
 * operando, no solo que lo sea, para permitir que se cambie su propia
 * contrasena y a la vez impedir que le cambie la de otro administrador.
 */
@Component
@RequiredArgsConstructor
public class AdminAutenticado {

    private final UserRepository userRepository;

    /** El usuario que esta operando, o 403 si no tiene ROLE_ADMIN. */
    public User exigir() {
        User usuario = usuarioActual();
        boolean esAdmin = usuario.getRoles().stream()
                .anyMatch(rol -> RolesEnum.ADMIN.getName().equalsIgnoreCase(rol.getName()));
        if (!esAdmin) {
            throw new GeneralException(
                    "El usuario autenticado no es administrador y no puede realizar esta accion",
                    "403"
            );
        }
        return usuario;
    }

    // Mismo camino que MedicoAutenticado.usuarioActual(): JwtFilter deja en
    // el principal el user_id como String, no un CustomUserDetails.
    private User usuarioActual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || autenticacion.getName() == null) {
            throw new GeneralException("No hay un usuario autenticado", "401");
        }

        int userId;
        try {
            userId = Integer.parseInt(autenticacion.getName());
        } catch (NumberFormatException e) {
            throw new GeneralException("El token no identifica a un usuario valido", "401");
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException("El token no identifica a un usuario valido", "401"));
    }
}
