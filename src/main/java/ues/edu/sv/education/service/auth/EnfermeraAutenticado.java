package ues.edu.sv.education.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.model.entity.Enfermera;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.repository.EnfermeraRepository;
import ues.edu.sv.education.repository.UserRepository;

import java.util.Optional;

/**
 * Resuelve QUE ENFERMERA esta operando, a partir del token y solo del token.
 *
 * Gemelo de MedicoAutenticado y por la misma razon: una toma de constantes sin
 * responsable identificable no es un registro clinico. Si el id de quien tomo
 * viniera en el cuerpo de la peticion, cualquiera podria firmar una presion
 * arterial a nombre de otra persona, y esa firma es justamente lo que hace que
 * el dato valga algo cuando el medico lo lee.
 *
 * El camino es users -> persona -> enfermeras, entero, porque JwtFilter deja
 * en el principal el user_id como String mientras que las tablas clinicas se
 * identifican por persona_id.
 *
 * Tener ROLE_ENFERMERA en el token y tener fila en `enfermeras` no es lo
 * mismo. Lo que autoriza a tomar constantes es la fila, no la etiqueta.
 */
@Component
@RequiredArgsConstructor
public class EnfermeraAutenticado {

    private final UserRepository userRepository;
    private final EnfermeraRepository enfermeraRepository;

    /**
     * La enfermera que esta operando, o 403 si quien opera no lo es.
     *
     * Es 403 y no 401 por lo mismo que en MedicoAutenticado: el usuario esta
     * autenticado, lo que le falta es la condicion para el acto que pidio. Un
     * MEDICO cae aqui, y debe caer: el enunciado de esta funcion es que
     * enfermeria registra y el medico lee.
     */
    public Enfermera exigir() {
        return buscar().orElseThrow(() -> new GeneralException(
                "El usuario autenticado no esta registrado como personal de enfermeria "
                        + "y no puede registrar signos vitales",
                "403"
        ));
    }

    /** La enfermera que esta operando, si lo es. Vacio si no. */
    public Optional<Enfermera> buscar() {
        return usuarioActual()
                .map(User::getPersona)
                .flatMap(persona -> enfermeraRepository.findById(persona.getPersonaId()));
    }

    private Optional<User> usuarioActual() {

        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();

        if (autenticacion == null || autenticacion.getName() == null) {
            throw new GeneralException("No hay un usuario autenticado", "401");
        }

        // El principal es el user_id como texto (ver JwtFilter). Un principal
        // que no se entiende es una sesion que no vale: 401, no un 500 por
        // NumberFormatException.
        int userId;
        try {
            userId = Integer.parseInt(autenticacion.getName());
        } catch (NumberFormatException e) {
            throw new GeneralException("El token no identifica a un usuario valido", "401");
        }

        return userRepository.findById(userId);
    }
}
