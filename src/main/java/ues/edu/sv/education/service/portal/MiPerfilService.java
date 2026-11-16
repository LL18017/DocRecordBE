package ues.edu.sv.education.service.portal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.model.dto.habitosestilodevida.HabitosEstiloDeVidaResponse;
import ues.edu.sv.education.model.dto.portal.MiPerfilDto;
import ues.edu.sv.education.model.dto.portal.PreferenciasDelPerfilRequest;
import ues.edu.sv.education.model.entity.Paciente;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.repository.PacienteRepository;
import ues.edu.sv.education.service.auth.PacienteAutenticado;
import ues.edu.sv.education.service.habitosestilodevida.HabitosEstiloDeVidaService;

import java.util.List;

/**
 * "Mi perfil" del paciente (DEF-07: HU-33 c4 y HU-14 c4).
 *
 * Igual que el portal (PortalDelPacienteService), ningun metodo recibe un id:
 * el paciente es siempre el de la sesion (PacienteAutenticado), asi que no hay
 * forma de pedir o cambiar el perfil de otro.
 *
 * Lo unico que se escribe es la preferencia de recordatorios, que no es un
 * dato clinico. No se toca modificado_por/modificado_en del paciente: esa
 * trazabilidad es de los cambios que hace el personal sobre la ficha.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MiPerfilService {

    private final PacienteAutenticado pacienteAutenticado;
    private final PacienteRepository pacienteRepository;
    private final HabitosEstiloDeVidaService habitosService;

    public MiPerfilDto miPerfil() {
        return aDto(pacienteAutenticado.exigir().paciente());
    }

    @Transactional
    public MiPerfilDto actualizarPreferencias(PreferenciasDelPerfilRequest request) {
        if (request == null || request.recibeRecordatorios() == null) {
            throw new GeneralException("Indique si desea recibir recordatorios (true o false)", "400");
        }
        Paciente paciente = pacienteAutenticado.exigir().paciente();
        paciente.setRecibeRecordatorios(request.recibeRecordatorios());
        return aDto(pacienteRepository.save(paciente));
    }

    /** Los habitos ACTIVOS del paciente de la sesion; los anulados no se muestran aqui. */
    public List<HabitosEstiloDeVidaResponse> misHabitos() {
        return habitosService.listarActivosDelPaciente(
                pacienteAutenticado.exigir().paciente().getPersonaId());
    }

    private static MiPerfilDto aDto(Paciente paciente) {
        Persona persona = paciente.getPersona();
        return new MiPerfilDto(
                paciente.getPersonaId(),
                paciente.getExpediente(),
                persona.getNombres(),
                persona.getApellidos(),
                persona.getEmail(),
                persona.getTelefono(),
                paciente.isRecibeRecordatorios());
    }
}
