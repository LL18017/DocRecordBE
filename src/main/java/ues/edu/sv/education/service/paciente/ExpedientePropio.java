package ues.edu.sv.education.service.paciente;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.service.auth.UsuarioAutenticado;

import java.util.Objects;

/**
 * Nadie escribe en su propio expediente clinico.
 *
 * Una cuenta puede ser a la vez personal y paciente: el medico o la enfermera
 * que tambien se atiende en la clinica, o el administrador con expediente
 * (HU-34, y el rol PACIENTE desde Usuarios y Roles). La persona es una sola,
 * asi que el expediente del paciente y la cuenta del personal comparten
 * persona_id. Sin esta regla, ese medico podia registrarse consultas,
 * recetarse, anular lo que otro le registro o, como administrador, aprobar la
 * anulacion de una consulta suya: un conflicto de interes que ninguna clinica
 * acepta, y que los permisos por rol no ven porque el rol es legitimo.
 *
 * Leer su expediente si puede: desde las pantallas del personal, como el de
 * cualquier paciente, y desde su portal. Lo que se cierra es ESCRIBIR en lo
 * clinico -consultas, recetas, signos vitales, alergias, antecedentes,
 * habitos, condiciones hereditarias, enfermedades cronicas- y anular o
 * resolver anulaciones. Los datos de identidad de la ficha (nombre, telefono,
 * direccion) quedan fuera a proposito: no son un acto clinico, y es justo lo
 * que hace falta para corregir el nombre de una cuenta que nacio con uno
 * provisional.
 *
 * Se compara la persona de la sesion con la del paciente, nunca el rol, y se
 * responde 403 con un mensaje que dice que hacer: que lo atienda un colega.
 */
@Component
@RequiredArgsConstructor
public class ExpedientePropio {

    public static final String MENSAJE =
            "No puede registrar ni modificar información clínica de su propio expediente. "
                    + "Pídale a otro miembro del personal que lo haga.";

    public static final String MENSAJE_ANULACION =
            "No puede aprobar ni rechazar la anulación de un documento de su propio expediente.";

    private final UsuarioAutenticado usuarioAutenticado;

    /** 403 si el paciente es la misma persona que esta operando. */
    public void exigirAjeno(Long pacientePersonaId) {
        exigirAjeno(pacientePersonaId, MENSAJE);
    }

    /** Igual, con el mensaje de aprobar o rechazar una anulacion. */
    public void exigirAjenoParaResolver(Long pacientePersonaId) {
        exigirAjeno(pacientePersonaId, MENSAJE_ANULACION);
    }

    /**
     * Para el modulo heredado de enfermedades cronicas, que cuelga el registro
     * de una cuenta (user_id) y no de un paciente: ahi "su propio expediente"
     * es la cuenta de la sesion.
     */
    public void exigirCuentaAjena(Integer userId) {
        User quien = usuarioAutenticado.exigir();
        if (Objects.equals(quien.getUserID(), userId)) {
            throw new GeneralException(MENSAJE, "403");
        }
    }

    private void exigirAjeno(Long pacientePersonaId, String mensaje) {
        if (pacientePersonaId == null) return;
        User quien = usuarioAutenticado.exigir();
        if (quien.getPersona() != null
                && Objects.equals(quien.getPersona().getPersonaId(), pacientePersonaId)) {
            throw new GeneralException(mensaje, "403");
        }
    }
}
