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
import ues.edu.sv.education.model.dto.prescripcion.AlertaDeAlergiaDto;
import ues.edu.sv.education.model.dto.prescripcion.DecisionDeAlertaDto;
import ues.edu.sv.education.model.dto.prescripcion.MedicamentoRequestDto;
import ues.edu.sv.education.model.dto.prescripcion.MedicamentoResponseDto;
import ues.edu.sv.education.model.dto.prescripcion.MedicoFirmaDto;
import ues.edu.sv.education.model.dto.prescripcion.PrescripcionRequestDto;
import ues.edu.sv.education.model.dto.prescripcion.PrescripcionResponseDto;
import ues.edu.sv.education.model.entity.*;
import ues.edu.sv.education.model.enums.EventCodeEnums;
import ues.edu.sv.education.model.enums.EventStatusEnums;
import ues.edu.sv.education.model.enums.SeveridadDeAlergia;
import ues.edu.sv.education.repository.*;
import ues.edu.sv.education.service.auth.MedicoAutenticado;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Prescripciones (epica E7).
 * <p>
 * Una receta es un documento con responsable legal. De ahi las dos decisiones
 * que gobiernan este servicio: la firma sale del token y nunca del cuerpo, y
 * una receta sin medicamentos no se emite.
 */
@Service
@RequiredArgsConstructor
public class PrescripcionService {

    private final PrescripcionRepository prescripcionRepository;
    private final ConsultaRepository consultaRepository;
    private final MedicamentoRepository medicamentoRepository;
    private final MedicoAutenticado medicoAutenticado;
    private final EventTypeRepository eventTypeRepository;
    private final EventRepository eventRepository;
    private final EventStatusRepository eventStatusRepository;
    private final AlergiaRepository alergiaRepository;
    private final PacienteRepository pacienteRepository;

    /**
     * Emite una receta a partir de una consulta.
     * <p>
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

        Map<Long, Medicamento> catalogo = medicamentosRecetables(renglones);

        // HU-24: la alerta del formulario es una ayuda; la regla vive aqui,
        // porque un cliente puede saltarse la pantalla.
        List<AlertaDeAlergiaDto> alertas = exigirDecisionSobreAlergias(
                consulta.getPaciente().getPersonaId(), catalogo, request);
        String justificacion = alertas.isEmpty() ? null : request.justificacionAlergia().trim();

        Prescripcion prescripcion = Prescripcion.builder()
                .consulta(consulta)
                .medico(medico)
                .fecha(LocalDateTime.now())
                .justificacionAlergia(justificacion)
                .medicamentos(new ArrayList<>())
                .build();

        for (MedicamentoRequestDto renglon : renglones) {
            Medicamento medicamento = catalogo.get(renglon.medicamentoId());
            prescripcion.agregarMedicamento(PrescripcionMedicamento.builder()
                    .catalogo(medicamento)
                    // La foto del nombre (ver PrescripcionMedicamento.medicamento):
                    // lo que dice la receta no depende de lo que diga el
                    // catalogo manana.
                    .medicamento(medicamento.descripcion())
                    .dosis(textoONull(renglon.dosis()))
                    .frecuencia(textoONull(renglon.frecuencia()))
                    .duracion(textoONull(renglon.duracion()))
                    .build());
        }

        Prescripcion prescripcionEnt = prescripcionRepository.save(prescripcion);
        if (!alertas.isEmpty()) {
            registrarEnBitacora(prescripcionEnt, alertas, justificacion);
        }
        return toDto(prescripcionEnt);
    }

    /**
     * Las alergias vigentes del paciente que coinciden con un medicamento
     * (HU-24, criterios 1 y 4). Lista vacia si no hay ninguna: no es un error.
     * Ver AlergiaRepository.buscarCoincidentesConMedicamento para la regla de
     * coincidencia y su limitacion.
     */
    @Transactional(readOnly = true)
    public List<AlertaDeAlergiaDto> alertasDeAlergia(Long pacienteId, Long medicamentoId) {
        if (!pacienteRepository.existsById(pacienteId)) {
            throw new NoResourceFoundException("Paciente no encontrado", "404");
        }
        Medicamento medicamento = medicamentoRepository.findById(medicamentoId)
                .orElseThrow(() -> new NoResourceFoundException("Medicamento no encontrado", "404"));
        return alertasDe(pacienteId, medicamento);
    }

    private List<AlertaDeAlergiaDto> alertasDe(Long pacienteId, Medicamento medicamento) {
        return alergiaRepository
                .buscarCoincidentesConMedicamento(pacienteId, medicamento.getMedicamentoId())
                .stream()
                .map(a -> new AlertaDeAlergiaDto(
                        a.getAlergiaId(),
                        a.getSustancia(),
                        a.getSeveridad(),
                        a.getReaccion(),
                        medicamento.getMedicamentoId(),
                        medicamento.descripcion()))
                .toList();
    }

    /**
     * Criterios 2 y 3: si algun medicamento de la receta coincide con una
     * alergia vigente, la receta exige justificacion escrita, y si la alergia
     * es SEVERA, ademas una confirmacion explicita. Devuelve las coincidencias
     * para registrarlas despues. Es 409 y no 400: el cuerpo esta bien formado,
     * lo que choca es el estado clinico del paciente.
     */
    private List<AlertaDeAlergiaDto> exigirDecisionSobreAlergias(
            Long pacienteId, Map<Long, Medicamento> catalogo, PrescripcionRequestDto request) {

        List<AlertaDeAlergiaDto> alertas = new ArrayList<>();
        for (Medicamento medicamento : catalogo.values()) {
            alertas.addAll(alertasDe(pacienteId, medicamento));
        }
        if (alertas.isEmpty()) {
            return alertas;
        }

        String detalle = alertas.stream()
                .map(a -> a.sustancia() + " (" + a.severidad() + ") en " + a.medicamento())
                .collect(Collectors.joining("; "));

        String justificacion = request.justificacionAlergia();
        if (justificacion == null || justificacion.isBlank()) {
            throw new GeneralException("El paciente es alérgico a una sustancia de esta receta: " + detalle
                    + ". Escriba la justificación para recetarlo de todos modos", "409");
        }
        boolean hayAlergiaSevera = alertas.stream().anyMatch(a -> a.severidad() == SeveridadDeAlergia.SEVERA);
        if (hayAlergiaSevera && !Boolean.TRUE.equals(request.confirmaAlergiaSevera())) {
            throw new GeneralException("Hay una alergia SEVERA coincidente: " + detalle
                    + ". Confirme de forma explícita que quiere recetarlo", "409");
        }
        return alertas;
    }

    /**
     * Criterio 5: la alerta y la decision del medico quedan en la bitacora.
     * Un evento por receta, ya PROCESSED: es solo un registro y nada debe
     * tomarlo para enviarlo (EventProcessorService solo procesa PENDING).
     * La descripcion de la columna mide 500, y la justificacion sola puede
     * medir 500, asi que se recorta; la receta guarda la justificacion entera.
     */
    private void registrarEnBitacora(Prescripcion prescripcion, List<AlertaDeAlergiaDto> alertas,
                                     String justificacion) {
        String alertasTexto = alertas.stream()
                .map(a -> a.sustancia() + " " + a.severidad() + " (" + a.medicamento() + ")")
                .collect(Collectors.joining("; "));
        String descripcion = "Receta emitida pese a alergia: " + alertasTexto + ". Justificacion: " + justificacion;
        guardarEventoDeAlerta(EventCodeEnums.ALLERGY_ALERT_OVERRIDE, descripcion,
                String.valueOf(prescripcion.getPrescripcionId()));
    }

    /**
     * Criterio 5, la otra mitad: el medico vio la alerta y NO receto. Sin esto
     * la bitacora solo sabria de las alertas que terminaron en receta, y la
     * alerta que hizo cambiar de idea al medico (la que mas vale auditar)
     * quedaria sin rastro. No hay receta a la que colgarlo, asi que `ref`
     * identifica al paciente y al medicamento.
     * <p>
     * Se exige que la alerta exista: registrar un "desistio" de una alerta
     * que el sistema no habria mostrado ensuciaria la bitacora (409).
     */
    @Transactional
    public void registrarDecisionDeAlerta(DecisionDeAlertaDto decision) {
        medicoAutenticado.exigir();
        if (!pacienteRepository.existsById(decision.pacienteId())) {
            throw new NoResourceFoundException("Paciente no encontrado", "404");
        }
        Medicamento medicamento = medicamentoRepository.findById(decision.medicamentoId())
                .orElseThrow(() -> new NoResourceFoundException("Medicamento no encontrado", "404"));
        List<AlertaDeAlergiaDto> alertas = alertasDe(decision.pacienteId(), medicamento);
        if (alertas.isEmpty()) {
            throw new GeneralException(
                    "El paciente no tiene una alergia vigente que coincida con " + medicamento.descripcion()
                            + ": no hay alerta cuya decisión registrar", "409");
        }
        String alertasTexto = alertas.stream()
                .map(a -> a.sustancia() + " " + a.severidad())
                .collect(Collectors.joining("; "));
        guardarEventoDeAlerta(EventCodeEnums.ALLERGY_ALERT_DECLINED,
                "Alerta de alergia mostrada: " + alertasTexto + " (" + medicamento.descripcion()
                        + "). Decision del medico: " + decision.decision(),
                "paciente:" + decision.pacienteId() + ";medicamento:" + decision.medicamentoId());
    }

    /** Un evento de bitacora ya PROCESSED (es solo registro, nada lo envia). */
    private void guardarEventoDeAlerta(EventCodeEnums tipo, String descripcion, String ref) {
        if (descripcion.length() > 500) {
            descripcion = descripcion.substring(0, 497) + "...";
        }
        LocalDateTime ahora = LocalDateTime.now();
        eventRepository.save(Event.builder()
                .eventType(eventTypeRepository.getReferenceById(tipo.getId()))
                .eventStatus(eventStatusRepository.getReferenceById(EventStatusEnums.PROCESSED.getId()))
                .description(descripcion)
                .userEmail(medicoAutenticado.correoDeSesion())
                .ref(ref)
                .createdAt(ahora)
                .processedAt(ahora)
                .build());
    }

    @Transactional
    public void enviarReceta(long consultaId) {

        Consulta consulta = buscarConsultaOFallar(consultaId);

        // Una receta sin medicamentos no es una receta, es un papel firmado.
        // El @NotEmpty del DTO ya lo rechaza con 400; esto es la misma regla
        // dicha donde vive el dominio, para que siga siendo cierta aunque a
        // este servicio se le llame desde otro lado.
        List<Prescripcion> prescripciones = prescripcionRepository.buscarPorConsulta(consultaId);
        if (prescripciones == null || prescripciones.isEmpty()) {
            throw new GeneralException("Una receta debe llevar al menos un medicamento", "422");
        }
        Event event = Event.builder()
                .eventType(
                        eventTypeRepository.getReferenceById(EventCodeEnums.PREESCRIPTION_SEND.getId())
                )
                .description("Se ha realizado un preescripcion")
                .eventStatus(
                        eventStatusRepository.getReferenceById(EventStatusEnums.PENDING.getId())
                )
                .userEmail(consulta.getPaciente().getPersona().getEmail())
                .ref(String.valueOf(consultaId))
                .createdAt(LocalDateTime.now())
                .build();

        eventRepository.save(event);

    }

    // Tope de tamano de pagina: sin el, "todos" seguiria siendo posible con un
    // solo ?tamano=999999999, que es exactamente el problema que el filtro
    // obligatorio original queria evitar (ver el javadoc de listar).
    private static final int TAMANO_PAGINA_MAXIMO = 100;
    private static final int TAMANO_PAGINA_POR_DEFECTO = 20;

    /**
     * Historico de recetas, filtrado y paginado.
     * <p>
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
     * <p>
     * ── Decision 1: forma de la paginacion ────────────────────────────────
     * La respuesta es un PaginaDto: contenido + paginaActual + tamanoPagina +
     * totalElementos + totalPaginas. Esto evita reproducir la trampa que ya
     * existe en UserService.getAll(inicio, fin): ese metodo hace
     * PageRequest.of(inicio, fin) -es decir, "fin" se usa como TAMANO de
     * pagina, no como filas hasta la fila fin- y devuelve un List<> plano sin
     * ningun total, asi que una llamada descuidada se queda con las primeras
     * "fin" filas sin que nada le avise que falta el resto. Aqui el cliente
     * nunca tiene que adivinar: el total viaja siempre en la misma respuesta.
     * <p>
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
     * <p>
     * ── Decision 3: id que no existe -> pagina vacia, no 404 ──────────────
     * ConsultaService.buscarPacienteOFallar responde 404 para un pacienteId
     * inexistente, con el argumento de que una lista vacia se leeria como
     * "existe pero no tiene nada". Ese argumento vale cuando hay UN filtro
     * obligatorio: el pacienteId ES la pregunta completa ("el historial de
     * ESTA persona"), y si esa persona no existe la pregunta en si no tiene
     * sentido -es mas parecido a pedir /pacientes/{id} que a filtrar una
     * lista.
     * <p>
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
     * <p>
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

    /**
     * Los medicamentos de la receta, resueltos contra el catalogo.
     *
     * HU-23 criterio 3: la receta no acepta valores fuera del catalogo. El
     * formulario ya solo ofrece los del catalogo, pero un cliente puede mandar
     * cualquier id, asi que la regla vive aqui.
     *
     * Criterio 4: un medicamento desactivado tampoco se acepta en una receta
     * NUEVA. Las ya emitidas no pasan por aqui y lo siguen mostrando.
     *
     * Las dos negativas son 400 y no 404/409 porque lo que esta mal es el
     * cuerpo de la peticion: la receta pide algo que no se puede recetar. Un
     * 404 se leeria en el cliente como "la consulta ya no existe", que es lo
     * que significa en este endpoint.
     *
     * Se resuelven todos en un solo viaje a la base (findAllById), no uno por
     * renglon.
     */
    private Map<Long, Medicamento> medicamentosRecetables(List<MedicamentoRequestDto> renglones) {
        // El @NotNull del DTO ya lo rechaza; se repite por lo mismo que la
        // lista vacia: la regla es del dominio, no del formulario.
        if (renglones.stream().anyMatch(r -> r == null || r.medicamentoId() == null)) {
            throw new GeneralException("Elige el medicamento del catalogo", "400");
        }
        List<Long> ids = renglones.stream().map(MedicamentoRequestDto::medicamentoId).distinct().toList();
        Map<Long, Medicamento> encontrados = medicamentoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Medicamento::getMedicamentoId, Function.identity()));

        for (Long id : ids) {
            Medicamento medicamento = encontrados.get(id);
            if (medicamento == null) {
                throw new GeneralException(
                        "El medicamento con codigo " + id + " no existe en el catalogo", "400");
            }
            if (!medicamento.isActivo()) {
                throw new GeneralException(
                        "El medicamento " + medicamento.descripcion()
                                + " esta desactivado en el catalogo y ya no se puede recetar", "400");
            }
        }
        return encontrados;
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
                        // getMedicamentoId sobre el proxy perezoso no lo
                        // inicializa: el id ya viene en la fila del renglon.
                        m.getCatalogo() == null ? null : m.getCatalogo().getMedicamentoId(),
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
                medicamentos,
                prescripcion.getJustificacionAlergia()
        );
    }

    private Map<String, Object> datosCorreo(
            Prescripcion prescripcion
    ) {
        Map<String, Object> datos = new HashMap<>();

        Paciente paciente = prescripcion.getConsulta().getPaciente();
        Persona personaPaciente = paciente.getPersona();
        Persona personaMedico = prescripcion.getMedico().getPersona();

        datos.put(
                "nombrePaciente",
                personaPaciente.getNombres()
        );

        datos.put(
                "nombreMedico",
                personaMedico.getNombres() + " "
                        + personaMedico.getApellidos()
        );

        datos.put(
                "fecha",
                prescripcion.getFecha().toLocalDate().toString()
        );

        return datos;
    }
}
