
package ues.edu.sv.education.service.habitosestilodevida;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.habitosestilodevida.HabitoHistorialResponse;
import ues.edu.sv.education.model.dto.habitosestilodevida.HabitosEstiloDeVidaRequest;
import ues.edu.sv.education.model.dto.habitosestilodevida.HabitosEstiloDeVidaResponse;
import ues.edu.sv.education.model.entity.Enfermera;
import ues.edu.sv.education.model.entity.HabitosEstiloDeVida;
import ues.edu.sv.education.model.entity.Medico;
import ues.edu.sv.education.model.entity.Paciente;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.TipoHabito;
import ues.edu.sv.education.model.mappers.HabitosEstiloDeVidaMapper;
import ues.edu.sv.education.repository.HabitoHistorialRepository;
import ues.edu.sv.education.repository.HabitosEstiloDeVidaRepository;
import ues.edu.sv.education.repository.PacienteRepository;
import ues.edu.sv.education.repository.TipoHabitoRepository;
import ues.edu.sv.education.service.auth.EnfermeraAutenticado;
import ues.edu.sv.education.service.auth.MedicoAutenticado;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * HU-14 · Habitos y estilo de vida.
 *
 * Cada alta y cada edicion anotan quien (del token, nunca del cuerpo) y
 * cuando. Editar no pisa el pasado: la version anterior se guarda en el
 * historial antes de sobrescribirla, para poder ver la evolucion del habito.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HabitosEstiloDeVidaService {

    private final HabitosEstiloDeVidaRepository habitosRepository;
    private final PacienteRepository pacientesRepository;
    private final TipoHabitoRepository tipoHabitoRepository;
    private final HabitosEstiloDeVidaMapper habitosMapper;
    private final HabitoHistorialRepository historialRepository;
    private final MedicoAutenticado medicoAutenticado;
    private final EnfermeraAutenticado enfermeraAutenticado;

    @Transactional
    public HabitosEstiloDeVidaResponse crear(
            HabitosEstiloDeVidaRequest request) {

        Persona autor = exigirPersonalClinico();
        Paciente paciente = obtenerPaciente(request.getPacienteId());
        TipoHabito tipoHabito =
                obtenerTipoHabito(request.getTipoHabitoId());

        HabitosEstiloDeVida habito = habitosMapper.toEntity(
                request, paciente, tipoHabito, autor, ahora());

        return habitosMapper.toResponse(
                habitosRepository.save(habito)
        );
    }

    public List<HabitosEstiloDeVidaResponse> listar() {
        return habitosRepository.findAll()
                .stream()
                .map(habitosMapper::toResponse)
                .toList();
    }

    public HabitosEstiloDeVidaResponse buscarPorId(Long id) {
        return habitosMapper.toResponse(obtenerHabito(id));
    }

    public List<HabitosEstiloDeVidaResponse> listarPorPaciente(
            Long pacienteId) {

        // Verifica que el paciente exista, aunque no tenga hábitos.
        obtenerPaciente(pacienteId);

        return habitosRepository
                .findByPaciente_PersonaId(pacienteId)
                .stream()
                .map(habitosMapper::toResponse)
                .toList();
    }

    @Transactional
    public HabitosEstiloDeVidaResponse actualizar(
            Long id,
            HabitosEstiloDeVidaRequest request) {

        Persona autor = exigirPersonalClinico();
        HabitosEstiloDeVida habito = obtenerHabito(id);
        Paciente paciente = obtenerPaciente(request.getPacienteId());
        TipoHabito tipoHabito =
                obtenerTipoHabito(request.getTipoHabitoId());

        String frecuencia = request.getFrecuencia().trim();
        String observaciones = HabitosEstiloDeVidaMapper.textoONull(request.getObservaciones());

        // Un guardado sin cambios no es una nueva version: llenaria el
        // historial de copias identicas y movera "actualizado" sin motivo.
        boolean sinCambios = Objects.equals(habito.getDescripcion(), request.getDescripcion())
                && habito.getNivel() == request.getNivel()
                && Objects.equals(habito.getFrecuencia(), frecuencia)
                && Objects.equals(habito.getObservaciones(), observaciones)
                && habito.getTipoHabito().getId().equals(tipoHabito.getId())
                && habito.getPaciente().getPersonaId().equals(paciente.getPersonaId());
        if (sinCambios) {
            return habitosMapper.toResponse(habito);
        }

        // Criterio 2: lo que se reemplaza se conserva antes de sobrescribirlo.
        LocalDateTime ahora = ahora();
        historialRepository.save(habitosMapper.toHistorial(habito, autor, ahora));

        habito.setDescripcion(request.getDescripcion());
        habito.setNivel(request.getNivel());
        habito.setFrecuencia(frecuencia);
        habito.setObservaciones(observaciones);
        habito.setActualizadoEn(ahora);
        habito.setActualizadoPor(autor);
        habito.setPaciente(paciente);
        habito.setTipoHabito(tipoHabito);

        return habitosMapper.toResponse(
                habitosRepository.save(habito)
        );
    }

    /** Las versiones anteriores de un habito, la mas reciente primero. */
    public List<HabitoHistorialResponse> historial(Long id) {
        obtenerHabito(id);
        return historialRepository.findByHabito_IdOrderByHistorialIdDesc(id)
                .stream()
                .map(habitosMapper::toHistorialResponse)
                .toList();
    }

    @Transactional
    public void eliminar(Long id) {
        HabitosEstiloDeVida habito = obtenerHabito(id);

        try {
            habitosRepository.delete(habito);
            habitosRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new GeneralException(
                    "No se puede eliminar el hábito debido a restricciones de integridad",
                    "HABITO_DELETE_ERROR"
            );
        }
    }

    /**
     * Ahora, a la precision de la columna (microsegundos). Con nanosegundos la
     * respuesta del alta diria una hora y la base guardaria otra, y la
     * version que luego pasa al historial no coincidiria con la que se vio.
     */
    private static LocalDateTime ahora() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * La persona del medico o de la enfermera que esta operando, o 403. Se
     * exige la FILA en `medicos` / `enfermeras` y no solo el rol (misma regla
     * que AlergiaService): un usuario con el rol pero sin ficha no firma.
     */
    private Persona exigirPersonalClinico() {
        return medicoAutenticado.buscar().map(Medico::getPersona)
                .or(() -> enfermeraAutenticado.buscar().map(Enfermera::getPersona))
                .orElseThrow(() -> new GeneralException(
                        "El usuario autenticado no esta registrado como medico ni como enfermera "
                                + "y no puede modificar los habitos del paciente",
                        "403"));
    }

    private HabitosEstiloDeVida obtenerHabito(Long id) {
        return habitosRepository.findById(id)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontró el hábito con ID: " + id,
                        "HABITO_NOT_FOUND"
                ));
    }

    private Paciente obtenerPaciente(Long id) {
        return pacientesRepository.findByPersonaId(id)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontró el paciente con ID: " + id,
                        "PACIENTE_NOT_FOUND"
                ));
    }

    private TipoHabito obtenerTipoHabito(Integer id) {
        return tipoHabitoRepository.findById(id)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontró el tipo de hábito con ID: " + id,
                        "TIPO_HABITO_NOT_FOUND"
                ));
    }
}
