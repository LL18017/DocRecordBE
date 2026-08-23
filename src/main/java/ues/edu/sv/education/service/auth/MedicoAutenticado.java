package ues.edu.sv.education.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.model.entity.Medico;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.repository.MedicoRepository;
import ues.edu.sv.education.repository.UserRepository;

import java.util.Optional;

/**
 * Resuelve QUE MEDICO esta operando, a partir del token y solo del token.
 *
 * Existe porque el dominio clinico entero depende de esta pregunta: quien
 * atendio la consulta y quien firma la receta. La respuesta NUNCA puede venir
 * en el cuerpo de la peticion. Este backend ya cometio ese error una vez
 * -ClinicaService recibia el id del dueno en el request y cualquiera podia
 * operar clinicas ajenas cambiando un numero-, y en el expediente clinico el
 * mismo error es peor: significaria poder escribir un diagnostico o firmar una
 * receta a nombre de otro medico.
 *
 * El camino es users -> persona -> medicos, y hace falta recorrerlo entero
 * porque JwtFilter deja en el principal el user_id como String (no un
 * CustomUserDetails), mientras que las tablas clinicas se identifican por
 * persona_id.
 *
 * Ojo con el rol: tener ROLE_MEDICO en el token y tener fila en `medicos` no
 * es lo mismo. Lo que autoriza a atender es la fila, no la etiqueta, asi que
 * es la fila lo que se exige aqui.
 */
@Component
@RequiredArgsConstructor
public class MedicoAutenticado {

    private final UserRepository userRepository;
    private final MedicoRepository medicoRepository;

    /**
     * El medico que esta operando, o 403 si quien opera no es medico.
     *
     * Es 403 y no 401: el usuario esta perfectamente autenticado, lo que no
     * tiene es la condicion que hace falta para el acto que pidio. Un ADMIN
     * que no ejerce entra en este caso, y debe entrar: administrar el sistema
     * no es atender pacientes.
     */
    public Medico exigir() {
        return buscar().orElseThrow(() -> new GeneralException(
                "El usuario autenticado no esta registrado como medico y no puede realizar este acto clinico",
                "403"
        ));
    }

    /** El medico que esta operando, si lo es. Vacio si no. */
    public Optional<Medico> buscar() {
        return usuarioActual()
                .map(User::getPersona)
                .flatMap(persona -> medicoRepository.findById(persona.getPersonaId()));
    }

    private Optional<User> usuarioActual() {

        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();

        if (autenticacion == null || autenticacion.getName() == null) {
            throw new GeneralException("No hay un usuario autenticado", "401");
        }

        // El principal es el user_id como texto (ver JwtFilter). Si algun dia
        // deja de serlo, esto debe fallar con 401 y no con un 500 por
        // NumberFormatException: un principal que no se entiende es una
        // sesion que no vale.
        int userId;
        try {
            userId = Integer.parseInt(autenticacion.getName());
        } catch (NumberFormatException e) {
            throw new GeneralException("El token no identifica a un usuario valido", "401");
        }

        return userRepository.findById(userId);
    }
}
