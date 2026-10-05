package ues.edu.sv.education.service.Alergia;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.Alergia.AlergiaRequest;
import ues.edu.sv.education.model.dto.Alergia.AlergiaResponse;
import ues.edu.sv.education.model.entity.Alergia;
import ues.edu.sv.education.model.entity.Enfermera;
import ues.edu.sv.education.model.entity.Medico;
import ues.edu.sv.education.model.entity.Paciente;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.repository.AlergiaRepository;
import ues.edu.sv.education.repository.PacienteRepository;
import ues.edu.sv.education.service.auth.EnfermeraAutenticado;
import ues.edu.sv.education.service.auth.MedicoAutenticado;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * HU-11 · Alergias del paciente.
 *
 * ── Quien firma ────────────────────────────────────────────────────────────
 * Registran y dan de baja el medico y la enfermera, y en los dos casos queda
 * anotado quien fue (criterio 4). Sale del token, nunca del cuerpo: misma
 * defensa que MedicoAutenticado. Y se exige la FILA en `medicos` o en
 * `enfermeras`, no solo el rol: un ADMIN que no ejerce recibe 403 aqui aunque
 * el controlador lo dejara pasar. Hoy el controlador ya lo deja fuera; esta
 * es la segunda cerradura.
 *
 * ── Por que baja logica ────────────────────────────────────────────────────
 * Una alergia es informacion de seguridad del paciente. Si alguien la quita
 * por error y la fila desaparece, no queda forma de saber que existio ni quien
 * la quito. Por eso eliminar solo la marca, y los listados la dejan de ver.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AlergiaService {

    /**
     * Las severas primero (criterio 2: son las que no se pueden pasar por
     * alto), luego las moderadas y al final las leves; dentro de cada
     * severidad, por sustancia. El enum va de menor a mayor, de ahi el
     * reverseOrder().
     */
    private static final Comparator<Alergia> SEVERAS_PRIMERO =
            Comparator.comparing(Alergia::getSeveridad, Comparator.reverseOrder())
                    .thenComparing(a -> a.getSustancia().toLowerCase());

    private final AlergiaRepository alergiaRepository;
    private final PacienteRepository pacienteRepository;
    private final MedicoAutenticado medicoAutenticado;
    private final EnfermeraAutenticado enfermeraAutenticado;

    public AlergiaResponse crear(AlergiaRequest request) {
        Persona autor = exigirPersonalClinico();
        Paciente paciente = exigirPaciente(request.pacienteId());
        String sustancia = request.sustancia().trim();

        // Criterio 3: la misma sustancia no se registra dos veces. El mensaje
        // nombra el registro que ya existe para que quien lo intenta vea que
        // la alergia ya esta y con que severidad, en vez de un "duplicado"
        // que no le dice que hacer.
        alergiaRepository.buscarVigentePorSustancia(paciente.getPersonaId(), sustancia)
                .ifPresent(existente -> {
                    throw new GeneralException(
                            "El paciente ya tiene registrada la alergia a " + existente.getSustancia()
                                    + " (severidad " + existente.getSeveridad()
                                    + ", detectada el " + existente.getFechaDeteccion()
                                    + "). Si hay que corregirla, elimine ese registro y vuelva a agregarla.",
                            "409");
                });

        Alergia alergia = Alergia.builder()
                .paciente(paciente)
                .sustancia(sustancia)
                .reaccion(request.reaccion().trim())
                .severidad(request.severidad())
                .fechaDeteccion(request.fechaDeteccion())
                .registradaPor(autor)
                .registradaEn(LocalDateTime.now())
                .build();

        return toResponse(alergiaRepository.save(alergia));
    }

    /**
     * Las vigentes, severas primero. Lista vacia si no tiene ninguna; 404 si
     * el paciente no existe: ahi el paciente es el recurso que se pide.
     */
    @Transactional(readOnly = true)
    public List<AlergiaResponse> listarDelPaciente(Long pacienteId) {
        exigirPaciente(pacienteId);
        return alergiaRepository.findByPaciente_PersonaIdAndEliminadaEnIsNull(pacienteId)
                .stream()
                .sorted(SEVERAS_PRIMERO)
                .map(AlergiaService::toResponse)
                .toList();
    }

    /**
     * Una alergia por id, vigente o no. Es la unica via por la que se ve una
     * dada de baja, con quien la elimino y cuando: el rastro del criterio 4.
     */
    @Transactional(readOnly = true)
    public AlergiaResponse obtenerPorId(Integer id) {
        return toResponse(exigirAlergia(id));
    }

    /** Baja logica. 404 si no existe o ya estaba dada de baja. */
    public void eliminar(Integer id) {
        Persona autor = exigirPersonalClinico();
        Alergia alergia = exigirAlergia(id);
        if (!alergia.estaVigente()) {
            throw new NoResourceFoundException("Alergia no encontrada", "404");
        }
        alergia.setEliminadaPor(autor);
        alergia.setEliminadaEn(LocalDateTime.now());
    }

    /**
     * La persona del medico o la enfermera que esta operando, o 403. Se
     * prueba primero medico; si una misma persona es las dos cosas da igual
     * cual responda, porque la persona es la misma.
     */
    private Persona exigirPersonalClinico() {
        return medicoAutenticado.buscar().map(Medico::getPersona)
                .or(() -> enfermeraAutenticado.buscar().map(Enfermera::getPersona))
                .orElseThrow(() -> new GeneralException(
                        "El usuario autenticado no esta registrado como medico ni como enfermera "
                                + "y no puede modificar las alergias del paciente",
                        "403"));
    }

    private Paciente exigirPaciente(Long pacienteId) {
        return pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontro el paciente con id: " + pacienteId, "404"));
    }

    private Alergia exigirAlergia(Integer id) {
        return alergiaRepository.findById(id)
                .orElseThrow(() -> new NoResourceFoundException("Alergia no encontrada", "404"));
    }

    private static AlergiaResponse toResponse(Alergia a) {
        return new AlergiaResponse(
                a.getAlergiaId(),
                a.getPaciente().getPersonaId(),
                a.getSustancia(),
                a.getReaccion(),
                a.getSeveridad(),
                a.getFechaDeteccion(),
                nombreDe(a.getRegistradaPor()),
                a.getRegistradaEn(),
                nombreDe(a.getEliminadaPor()),
                a.getEliminadaEn()
        );
    }

    private static String nombreDe(Persona persona) {
        return persona == null ? null : persona.getNombres() + " " + persona.getApellidos();
    }
}
