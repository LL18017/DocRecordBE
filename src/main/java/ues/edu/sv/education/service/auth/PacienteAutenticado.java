package ues.edu.sv.education.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.model.entity.Paciente;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.repository.PacienteRepository;

/**
 * Resuelve QUE PACIENTE esta mirando su expediente, a partir del token y solo
 * del token (HU-34).
 *
 * Es el gemelo de MedicoAutenticado del otro lado del mostrador, y la razon es
 * la misma pero con mas en juego: si el portal aceptara el id del paciente en
 * la URL o en el cuerpo, cualquier paciente con cuenta podria leer el
 * expediente de otro cambiando un numero (HU-34 criterio 2). Por eso ningun
 * endpoint de /mi recibe un id: el paciente es siempre el de la sesion.
 *
 * El camino es users -> persona -> pacientes, entero, porque JwtFilter deja en
 * el principal el user_id mientras que las tablas clinicas se identifican por
 * persona_id.
 *
 * Tener ROLE_PACIENTE en el token y tener fila en `pacientes` no es lo mismo:
 * lo que hace que haya un expediente que mostrar es la fila, asi que es la
 * fila lo que se exige.
 */
@Component
@RequiredArgsConstructor
public class PacienteAutenticado {

    private final UsuarioAutenticado usuarioAutenticado;
    private final PacienteRepository pacienteRepository;

    /** La cuenta que inicio sesion y el expediente que le corresponde. */
    public record EnSesion(User cuenta, Paciente paciente) {
    }

    /**
     * El paciente de la sesion, o 403 si la cuenta no esta vinculada a un
     * expediente.
     *
     * 403 y no 404: el usuario esta autenticado y lo que pide existe como
     * funcion; lo que le falta es la condicion para usarla. Un 404 se leeria
     * como "su expediente se perdio", que es una afirmacion falsa y alarmante
     * para quien la reciba.
     */
    public EnSesion exigir() {
        User cuenta = usuarioAutenticado.exigir();
        Paciente paciente = pacienteRepository.findById(cuenta.getPersona().getPersonaId())
                .orElseThrow(() -> new GeneralException(
                        "Su cuenta no está vinculada a un expediente de paciente", "403"));
        return new EnSesion(cuenta, paciente);
    }
}
