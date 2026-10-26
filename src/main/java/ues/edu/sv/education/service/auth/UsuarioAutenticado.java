package ues.edu.sv.education.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.repository.UserRepository;

/**
 * Quien esta operando, sea cual sea su rol, a partir del token y solo del
 * token. Es MedicoAutenticado / AdminAutenticado sin exigir ningun rol: sirve
 * para dejar constancia de QUIEN hizo algo (p. ej. quien registro o modifico a
 * un paciente), no para decidir si puede hacerlo.
 */
@Component
@RequiredArgsConstructor
public class UsuarioAutenticado {

    private final UserRepository userRepository;

    /** El usuario del token, o 401 si no hay sesion o el token no lo identifica. */
    public User exigir() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || autenticacion.getName() == null) {
            throw new GeneralException("No hay un usuario autenticado", "401");
        }

        // El principal es el user_id como texto (ver JwtFilter).
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
