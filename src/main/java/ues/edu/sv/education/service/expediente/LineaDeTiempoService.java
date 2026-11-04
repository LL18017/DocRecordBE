package ues.edu.sv.education.service.expediente;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.consulta.ConsultaResponseDto;
import ues.edu.sv.education.model.dto.expediente.EventoDelExpedienteDto;
import ues.edu.sv.education.model.dto.expediente.ProfesionalDelEventoDto;
import ues.edu.sv.education.model.dto.prescripcion.PrescripcionResponseDto;
import ues.edu.sv.education.model.dto.signosvitales.SignosVitalesResponseDto;
import ues.edu.sv.education.model.enums.TipoDeEventoDelExpediente;
import ues.edu.sv.education.repository.PacienteRepository;
import ues.edu.sv.education.service.consulta.ConsultaService;
import ues.edu.sv.education.service.prescripcion.PrescripcionService;
import ues.edu.sv.education.service.signosvitales.SignosVitalesService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * La linea de tiempo del expediente (HU-15): consultas, tomas de signos
 * vitales y recetas de un paciente en una sola lista, de lo mas reciente a lo
 * mas antiguo.
 *
 * ── Por que se arma con los servicios de cada tipo ────────────────────────
 * Cada uno ya sabe como se lee su registro y quien puede ver que: el recorte
 * del diagnostico para enfermeria vive en ConsultaService, el IMC derivado en
 * SignosVitalesService, el estado del envio por correo en PrescripcionService.
 * Repetir esas reglas aqui con consultas propias daria dos versiones de la
 * misma verdad, y la linea de tiempo es justo el lugar donde no pueden
 * contradecirse con la pantalla de cada tipo.
 *
 * ── Toda la red, no la sede activa ────────────────────────────────────────
 * Nada se filtra por la clinica de quien pregunta. El objetivo de la HU es que
 * el medico entienda la evolucion del paciente "aunque lo hayan atendido en
 * otra clinica de la red"; por eso cada evento dice donde ocurrio en lugar de
 * esconder lo que no ocurrio aqui.
 *
 * ── Anulados incluidos ────────────────────────────────────────────────────
 * Una consulta, una toma o una receta anulada sigue siendo parte de la
 * historia (HU-16, HU-21, HU-22): aparece con su estado ANULADA y su motivo,
 * no desaparece.
 *
 * Es de solo lectura (criterio 5): no hay nada en este servicio que escriba.
 */
@Service
@RequiredArgsConstructor
public class LineaDeTiempoService {

    // Mismo instante: primero la consulta, luego su toma, luego su receta. No
    // es lo habitual -la toma precede a la consulta y la receta la sigue-, pero
    // con fechas capturadas a mano puede pasar, y el orden tiene que ser el
    // mismo en cada llamada.
    private static final Comparator<EventoDelExpedienteDto> MAS_RECIENTE_PRIMERO =
            Comparator.comparing(EventoDelExpedienteDto::fecha, Comparator.reverseOrder())
                    .thenComparing(EventoDelExpedienteDto::tipo)
                    .thenComparing(EventoDelExpedienteDto::id, Comparator.reverseOrder());

    private final PacienteRepository pacienteRepository;
    private final ConsultaService consultaService;
    private final SignosVitalesService signosVitalesService;
    private final PrescripcionService prescripcionService;

    /** 404 si el paciente no existe: aqui el paciente es el recurso que se pide. */
    @Transactional(readOnly = true)
    public List<EventoDelExpedienteDto> delPaciente(Long pacienteId) {
        if (!pacienteRepository.existsById(pacienteId)) {
            throw new NoResourceFoundException("Paciente no encontrado", "404");
        }

        List<EventoDelExpedienteDto> eventos = new ArrayList<>();
        consultaService.listar(pacienteId, null, null, null).forEach(c -> eventos.add(deConsulta(c)));
        signosVitalesService.todasDelPaciente(pacienteId).forEach(t -> eventos.add(deToma(t)));
        prescripcionService.todasDelPaciente(pacienteId).forEach(r -> eventos.add(deReceta(r)));

        eventos.sort(MAS_RECIENTE_PRIMERO);
        return eventos;
    }

    private static EventoDelExpedienteDto deConsulta(ConsultaResponseDto consulta) {
        var medico = consulta.medico();
        return new EventoDelExpedienteDto(
                TipoDeEventoDelExpediente.CONSULTA,
                consulta.consultaId(),
                consulta.fecha(),
                consulta.estado().name(),
                consulta.clinica(),
                new ProfesionalDelEventoDto(medico.personaId(), medico.nombres(), medico.apellidos(), "MEDICO"),
                consulta, null, null);
    }

    private static EventoDelExpedienteDto deToma(SignosVitalesResponseDto toma) {
        var enfermera = toma.enfermera();
        return new EventoDelExpedienteDto(
                TipoDeEventoDelExpediente.SIGNOS_VITALES,
                toma.signosVitalesId(),
                toma.tomadoEn(),
                toma.estado(),
                toma.clinica(),
                new ProfesionalDelEventoDto(
                        enfermera.personaId(), enfermera.nombres(), enfermera.apellidos(), "ENFERMERA"),
                null, toma, null);
    }

    private static EventoDelExpedienteDto deReceta(PrescripcionResponseDto receta) {
        var medico = receta.medico();
        return new EventoDelExpedienteDto(
                TipoDeEventoDelExpediente.RECETA,
                receta.prescripcionId(),
                receta.fecha(),
                receta.estado().name(),
                receta.clinica(),
                new ProfesionalDelEventoDto(medico.personaId(), medico.nombres(), medico.apellidos(), "MEDICO"),
                null, null, receta);
    }
}
