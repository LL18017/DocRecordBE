package ues.edu.sv.education.service.prescripcion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.prescripcion.MedicamentoRequestDto;
import ues.edu.sv.education.model.dto.prescripcion.MedicamentoResponseDto;
import ues.edu.sv.education.model.dto.prescripcion.MedicoFirmaDto;
import ues.edu.sv.education.model.dto.prescripcion.PrescripcionRequestDto;
import ues.edu.sv.education.model.dto.prescripcion.PrescripcionResponseDto;
import ues.edu.sv.education.model.entity.Consulta;
import ues.edu.sv.education.model.entity.Medico;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.Prescripcion;
import ues.edu.sv.education.model.entity.PrescripcionMedicamento;
import ues.edu.sv.education.repository.ConsultaRepository;
import ues.edu.sv.education.repository.PacienteRepository;
import ues.edu.sv.education.repository.PrescripcionRepository;
import ues.edu.sv.education.service.auth.MedicoAutenticado;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Prescripciones (epica E7).
 *
 * Una receta es un documento con responsable legal. De ahi las dos decisiones
 * que gobiernan este servicio: la firma sale del token y nunca del cuerpo, y
 * una receta sin medicamentos no se emite.
 */
@Service
@RequiredArgsConstructor
public class PrescripcionService {

    private final PrescripcionRepository prescripcionRepository;
    private final ConsultaRepository consultaRepository;
    private final PacienteRepository pacienteRepository;
    private final MedicoAutenticado medicoAutenticado;

    /**
     * Emite una receta a partir de una consulta.
     *
     * Firma el medico autenticado, que no tiene por que ser el mismo que
     * atendio la consulta: en una clinica con turnos, el medico de guardia
     * receta sobre una consulta que abrio otro. Por eso la firma se guarda en
     * la receta y no se deduce de la consulta (ver el comentario de
     * prescripciones.medico_id en V6): asi queda registrado quien respondio
     * por ESTE documento.
     */
    @Transactional
    public PrescripcionResponseDto crear(PrescripcionRequestDto request) {

        Medico medico = medicoAutenticado.exigir();
        Consulta consulta = buscarConsultaOFallar(request.consultaId());

        // Una receta sin medicamentos no es una receta, es un papel firmado.
        // El @NotEmpty del DTO ya lo rechaza con 400; esto es la misma regla
        // dicha donde vive el dominio, para que siga siendo cierta aunque a
        // este servicio se le llame desde otro lado.
        List<MedicamentoRequestDto> renglones = request.medicamentos();
        if (renglones == null || renglones.isEmpty()) {
            throw new GeneralException("Una receta debe llevar al menos un medicamento", "422");
        }

        Prescripcion prescripcion = Prescripcion.builder()
                .consulta(consulta)
                .medico(medico)
                .fecha(LocalDateTime.now())
                .medicamentos(new ArrayList<>())
                .build();

        for (MedicamentoRequestDto renglon : renglones) {
            prescripcion.agregarMedicamento(PrescripcionMedicamento.builder()
                    .medicamento(renglon.medicamento().trim())
                    .dosis(textoONull(renglon.dosis()))
                    .frecuencia(textoONull(renglon.frecuencia()))
                    .duracion(textoONull(renglon.duracion()))
                    .build());
        }

        return toDto(prescripcionRepository.save(prescripcion));
    }

    /**
     * Recetas de una consulta o de un paciente.
     *
     * Se exige uno de los dos filtros. Sin filtro esto devolveria la
     * medicacion de todos los pacientes del sistema en una sola respuesta, y
     * un listado asi no responde a ninguna pregunta real: solo sirve para
     * sacar datos.
     */
    @Transactional(readOnly = true)
    public List<PrescripcionResponseDto> listar(Long consultaId, Long pacienteId) {

        if (consultaId == null && pacienteId == null) {
            throw new GeneralException(
                    "Indique consultaId o pacienteId para listar recetas", "400");
        }
        if (consultaId != null && pacienteId != null) {
            throw new GeneralException(
                    "Indique consultaId o pacienteId, no ambos", "400");
        }

        if (consultaId != null) {
            buscarConsultaOFallar(consultaId);
            return prescripcionRepository.buscarPorConsulta(consultaId).stream().map(this::toDto).toList();
        }

        // Igual que en el historial de consultas: un paciente inexistente es
        // 404, no una lista vacia que se leeria como "no toma nada".
        if (!pacienteRepository.existsById(pacienteId)) {
            throw new NoResourceFoundException("Paciente no encontrado", "404");
        }

        return prescripcionRepository.buscarPorPaciente(pacienteId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PrescripcionResponseDto obtener(Long prescripcionId) {
        return toDto(buscarPrescripcionOFallar(prescripcionId));
    }

    /**
     * Anula una receta.
     *
     * Se borra entera, con sus renglones (cascade en Java y ON DELETE CASCADE
     * en la base): media receta -unos medicamentos si y otros no- seria un
     * documento distinto del que firmo el medico.
     */
    @Transactional
    public void eliminar(Long prescripcionId) {
        prescripcionRepository.delete(buscarPrescripcionOFallar(prescripcionId));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    private Consulta buscarConsultaOFallar(Long consultaId) {
        return consultaRepository.findById(consultaId)
                .orElseThrow(() -> new NoResourceFoundException("Consulta no encontrada", "404"));
    }

    private Prescripcion buscarPrescripcionOFallar(Long prescripcionId) {
        return prescripcionRepository.buscarConDetalle(prescripcionId)
                .orElseThrow(() -> new NoResourceFoundException("Receta no encontrada", "404"));
    }

    private String textoONull(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    private PrescripcionResponseDto toDto(Prescripcion prescripcion) {

        Persona personaMedico = prescripcion.getMedico().getPersona();

        List<MedicamentoResponseDto> medicamentos = prescripcion.getMedicamentos() == null
                ? List.of()
                : prescripcion.getMedicamentos().stream()
                .map(m -> new MedicamentoResponseDto(
                        m.getId(),
                        m.getMedicamento(),
                        m.getDosis(),
                        m.getFrecuencia(),
                        m.getDuracion()))
                .toList();

        return new PrescripcionResponseDto(
                prescripcion.getPrescripcionId(),
                prescripcion.getFecha(),
                prescripcion.getConsulta().getConsultaId(),
                new MedicoFirmaDto(
                        prescripcion.getMedico().getPersonaId(),
                        personaMedico.getNombres(),
                        personaMedico.getApellidos()
                ),
                medicamentos
        );
    }
}
