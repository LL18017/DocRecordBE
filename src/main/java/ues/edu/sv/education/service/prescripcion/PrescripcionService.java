package ues.edu.sv.education.service.prescripcion;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.common.PaginaDto;
import ues.edu.sv.education.model.dto.consulta.PacienteResumenDto;
import ues.edu.sv.education.model.dto.prescripcion.MedicamentoRequestDto;
import ues.edu.sv.education.model.dto.prescripcion.MedicamentoResponseDto;
import ues.edu.sv.education.model.dto.prescripcion.MedicoFirmaDto;
import ues.edu.sv.education.model.dto.prescripcion.PrescripcionRequestDto;
import ues.edu.sv.education.model.dto.prescripcion.PrescripcionResponseDto;
import ues.edu.sv.education.model.entity.Consulta;
import ues.edu.sv.education.model.entity.Medico;
import ues.edu.sv.education.model.entity.Paciente;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.Prescripcion;
import ues.edu.sv.education.model.entity.PrescripcionMedicamento;
import ues.edu.sv.education.repository.ConsultaRepository;
import ues.edu.sv.education.repository.PrescripcionRepository;
import ues.edu.sv.education.service.auth.MedicoAutenticado;

import java.time.LocalDate;
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

    // Tope de tamano de pagina: sin el, "todos" seguiria siendo posible con un
    // solo ?tamano=999999999, que es exactamente el problema que el filtro
    // obligatorio original queria evitar (ver el javadoc de listar).
    private static final int TAMANO_PAGINA_MAXIMO = 100;
    private static final int TAMANO_PAGINA_POR_DEFECTO = 20;

    /**
     * Historico de recetas, filtrado y paginado.
     *
     * ── Por que ya no exige un filtro ─────────────────────────────────────
     * Antes se obligaba a elegir consultaId O pacienteId (nunca los dos, ni
     * ninguno), con el argumento de que sin filtro la respuesta seria la
     * medicacion de TODOS los pacientes del sistema en una sola llamada. Ese
     * argumento sigue siendo cierto, pero la respuesta correcta a "no
     * devuelvas demasiado" es PAGINAR, no prohibir la pregunta: "el historico
     * completo de recetas" (para un panel, una auditoria, la tarjeta
     * "prescripciones hoy") es una pregunta legitima que el 400 anterior no
     * dejaba hacer. Ahora los cinco filtros (pacienteId, medicoId,
     * consultaId, desde, hasta) son opcionales y COMBINABLES entre si: "por
     * paciente Y por medico a la vez" -el caso que antes daba 400- es
     * justo lo que se pidio.
     *
     * ── Decision 1: forma de la paginacion ────────────────────────────────
     * La respuesta es un PaginaDto: contenido + paginaActual + tamanoPagina +
     * totalElementos + totalPaginas. Esto evita reproducir la trampa que ya
     * existe en UserService.getAll(inicio, fin): ese metodo hace
     * PageRequest.of(inicio, fin) -es decir, "fin" se usa como TAMANO de
     * pagina, no como filas hasta la fila fin- y devuelve un List<> plano sin
     * ningun total, asi que una llamada descuidada se queda con las primeras
     * "fin" filas sin que nada le avise que falta el resto. Aqui el cliente
     * nunca tiene que adivinar: el total viaja siempre en la misma respuesta.
     *
     * ── Decision 2: desde/hasta son FECHAS, el filtro trabaja en fecha-hora ─
     * prescripciones.fecha es LocalDateTime (el instante exacto en que se
     * firmo la receta), pero pedirle al cliente fecha-hora para "que se
     * receto esta semana" es pedirle un detalle que no tiene por que conocer.
     * desde/hasta llegan como LocalDate (yyyy-MM-dd) y se convierten aqui:
     * desde arranca a las 00:00:00.000000000 de ese dia (inclusive) y hasta
     * se compara contra el INICIO DEL DIA SIGUIENTE con "<" (exclusivo). Un
     * hasta tratado ingenuamente como "fecha <= hasta" en SQL/JPQL compara
     * contra las 00:00:00 de ese mismo dia y se COME el dia entero de hasta:
     * una receta firmada a las 23:59 de ese dia quedaria fuera del rango
     * aunque el usuario pidio "hasta ese dia". Con
     * fecha < hasta.plusDays(1) esa receta si entra, sin importar la hora.
     *
     * ── Decision 3: id que no existe -> pagina vacia, no 404 ──────────────
     * ConsultaService.buscarPacienteOFallar responde 404 para un pacienteId
     * inexistente, con el argumento de que una lista vacia se leeria como
     * "existe pero no tiene nada". Ese argumento vale cuando hay UN filtro
     * obligatorio: el pacienteId ES la pregunta completa ("el historial de
     * ESTA persona"), y si esa persona no existe la pregunta en si no tiene
     * sentido -es mas parecido a pedir /pacientes/{id} que a filtrar una
     * lista.
     *
     * Aqui es distinto a proposito, y no por capricho: los filtros son
     * varios, opcionales y combinables, y lo que se pregunta es "dame las
     * recetas que cumplan estas condiciones", no "dame el recurso
     * identificado por este id". Un pacienteId que no existe es, para ese
     * proposito, una condicion que ningun registro cumple -exactamente igual
     * que un rango de fechas sin coincidencias, donde nadie esperaria un 404.
     * El 404 ademas no compone: con pacienteId Y medicoId a la vez, si solo
     * uno de los dos no existe, ¿cual "gana" el 404? La respuesta uniforme
     * (pagina vacia, totalElementos=0) es la unica que se generaliza sin
     * casos raros a cualquier combinacion de filtros -incluido consultaId,
     * que hasta ahora si daba 404 por ser entonces el unico filtro posible.
     */
    @Transactional(readOnly = true)
    public PaginaDto<PrescripcionResponseDto> listar(
            Long consultaId,
            Long pacienteId,
            Long medicoId,
            LocalDate desde,
            LocalDate hasta,
            Integer pagina,
            Integer tamano) {

        int paginaSolicitada = pagina == null ? 0 : pagina;
        int tamanoSolicitado = tamano == null ? TAMANO_PAGINA_POR_DEFECTO : tamano;

        if (paginaSolicitada < 0) {
            throw new GeneralException("La pagina no puede ser negativa", "400");
        }
        if (tamanoSolicitado < 1 || tamanoSolicitado > TAMANO_PAGINA_MAXIMO) {
            throw new GeneralException(
                    "El tamano de pagina debe estar entre 1 y " + TAMANO_PAGINA_MAXIMO, "400");
        }

        // Ver decision 2 en el javadoc: desde arranca al inicio del dia,
        // hasta se compara EXCLUSIVO contra el inicio del dia siguiente para
        // no comerse el dia entero de "hasta".
        LocalDateTime desdeFechaHora = desde == null ? null : desde.atStartOfDay();
        LocalDateTime hastaFechaHora = hasta == null ? null : hasta.plusDays(1).atStartOfDay();

        Pageable pageable = PageRequest.of(
                paginaSolicitada,
                tamanoSolicitado,
                Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("prescripcionId")));

        Page<Prescripcion> resultado = prescripcionRepository.buscar(
                pacienteId, medicoId, consultaId, desdeFechaHora, hastaFechaHora, pageable);

        return PaginaDto.de(resultado.map(this::toDto));
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
        // buscarConDetalle (no findById) porque toDto() ahora necesita
        // consulta.getPaciente().getPersona() para armar el campo paciente
        // del DTO; con findById esa cadena se cargaria perezosamente en dos
        // consultas mas, en vez de venir ya resuelta en esta.
        return consultaRepository.buscarConDetalle(consultaId)
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
        Paciente paciente = prescripcion.getConsulta().getPaciente();
        Persona personaPaciente = paciente.getPersona();

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
                new PacienteResumenDto(
                        paciente.getPersonaId(),
                        paciente.getExpediente(),
                        personaPaciente.getNombres(),
                        personaPaciente.getApellidos()
                ),
                new MedicoFirmaDto(
                        prescripcion.getMedico().getPersonaId(),
                        personaMedico.getNombres(),
                        personaMedico.getApellidos()
                ),
                medicamentos
        );
    }
}
