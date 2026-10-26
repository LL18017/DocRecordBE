package ues.edu.sv.education.service.prescripcion;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.common.PaginaDto;
import ues.edu.sv.education.model.dto.consulta.ClinicaResumenDto;
import ues.edu.sv.education.model.dto.consulta.PacienteResumenDto;
import ues.edu.sv.education.model.dto.prescripcion.AlertaDeAlergiaDto;
import ues.edu.sv.education.model.dto.prescripcion.DecisionDeAlertaDto;
import ues.edu.sv.education.model.dto.prescripcion.MedicamentoRequestDto;
import ues.edu.sv.education.model.dto.prescripcion.MedicamentoResponseDto;
import ues.edu.sv.education.model.dto.prescripcion.MedicoFirmaDto;
import ues.edu.sv.education.model.dto.prescripcion.PrescripcionRequestDto;
import ues.edu.sv.education.model.dto.prescripcion.PrescripcionResponseDto;
import ues.edu.sv.education.model.entity.*;
import ues.edu.sv.education.model.enums.EstadoPrescripcion;
import ues.edu.sv.education.model.enums.EventCodeEnums;
import ues.edu.sv.education.model.enums.EventStatusEnums;
import ues.edu.sv.education.model.enums.SeveridadDeAlergia;
import ues.edu.sv.education.repository.*;
import ues.edu.sv.education.service.ReportService;
import ues.edu.sv.education.service.auth.MedicoAutenticado;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
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
    private final ReportService reportService;

    private static final DateTimeFormatter FECHA_PDF = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Emite una receta a partir de una consulta.
     * <p>
     * HU-22, criterio 1: la receta HEREDA de la consulta el paciente, el
     * medico, la clinica y la fecha; nada de eso se elige al emitirla.
     * <p>
     * Por eso la firma es el medico de la consulta y solo el puede emitirla:
     * antes firmaba "quien estuviera autenticado" (el medico de guardia
     * podia recetar sobre una consulta ajena), lo que dejaba una receta con
     * un medico distinto del que atendio y examino al paciente. La columna
     * prescripciones.medico_id se conserva (ver V6): es la firma del documento
     * y no debe cambiar si la consulta se corrige, pero se llena con el medico
     * de la consulta. Otro medico responde 403.
     */
    @Transactional
    public PrescripcionResponseDto crear(PrescripcionRequestDto request) {

        Medico medico = medicoAutenticado.exigir();
        Consulta consulta = buscarConsultaOFallar(request.consultaId());

        if (!consulta.getMedico().getPersonaId().equals(medico.getPersonaId())) {
            throw new GeneralException(
                    "Solo el médico que atendió la consulta puede emitir su receta", "403");
        }

        // Una receta sin medicamentos no es una receta, es un papel firmado.
        // El @NotEmpty del DTO ya lo rechaza con 400; esto es la misma regla
        // dicha donde vive el dominio, para que siga siendo cierta aunque a
        // este servicio se le llame desde otro lado.
        List<MedicamentoRequestDto> renglones = request.medicamentos();
        if (renglones == null || renglones.isEmpty()) {
            throw new GeneralException("Una receta debe llevar al menos un medicamento", "422");
        }

        exigirTratamientoCompleto(renglones);
        Map<Long, Medicamento> catalogo = medicamentosRecetables(renglones);

        // HU-24: la alerta del formulario es una ayuda; la regla vive aqui,
        // porque un cliente puede saltarse la pantalla.
        List<AlertaDeAlergiaDto> alertas = exigirDecisionSobreAlergias(
                consulta.getPaciente().getPersonaId(), catalogo, request);
        String justificacion = alertas.isEmpty() ? null : request.justificacionAlergia().trim();

        Prescripcion prescripcion = Prescripcion.builder()
                .consulta(consulta)
                .medico(consulta.getMedico())
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
                    .dosis(renglon.dosis().trim())
                    .viaAdministracion(renglon.viaAdministracion().trim())
                    .frecuencia(renglon.frecuencia().trim())
                    .duracion(renglon.duracion().trim())
                    .build());
        }

        Prescripcion prescripcionEnt = prescripcionRepository.save(prescripcion);
        if (!alertas.isEmpty()) {
            registrarEnBitacora(prescripcionEnt, alertas, justificacion);
        }

        // HU-25 criterio 1: la receta sale al correo del paciente sin que el
        // medico haga nada mas. Criterio 2: si el paciente no tiene correo no
        // se encola un envio vacio; la respuesta lo dice (SIN_CORREO) para que
        // la pantalla avise y ofrezca imprimirla.
        if (tieneCorreo(consulta)) {
            encolarEnvio(consulta);
        }
        return toDto(prescripcionEnt, enviosDe(List.of(prescripcionEnt)));
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

    /**
     * Envio manual (o reenvio) de la receta al correo del paciente. Es el
     * camino del boton "Enviar/Reenviar por correo", sobre todo cuando el
     * envio automatico fallo (HU-25, criterio 4).
     * <p>
     * Sin correo es 422 y no un evento: un envio sin destinatario no tiene a
     * quien llegar y la pantalla debe decir que se imprima.
     */
    @Transactional
    public void enviarReceta(long consultaId) {

        Consulta consulta = buscarConsultaOFallar(consultaId);

        List<Prescripcion> prescripciones = prescripcionRepository.buscarPorConsulta(consultaId);
        if (prescripciones == null || prescripciones.isEmpty()) {
            throw new GeneralException("La consulta no tiene recetas para enviar", "422");
        }
        if (!tieneCorreo(consulta)) {
            throw new GeneralException("El paciente no tiene un correo registrado. "
                    + "Registre su correo o imprima la receta para entregársela", "422");
        }
        encolarEnvio(consulta);
    }

    private static boolean tieneCorreo(Consulta consulta) {
        String correo = consulta.getPaciente().getPersona().getEmail();
        return correo != null && !correo.isBlank();
    }

    /**
     * Deja el envio PENDIENTE para que EventProcessorService lo mande: asi lo
     * lento del SMTP no cae sobre la peticion del medico. La referencia es la
     * consulta, y el correo lleva todas las recetas de esa consulta.
     */
    private void encolarEnvio(Consulta consulta) {
        eventRepository.save(Event.builder()
                .eventType(eventTypeRepository.getReferenceById(EventCodeEnums.PREESCRIPTION_SEND.getId()))
                .description("Se ha realizado un preescripcion")
                .eventStatus(eventStatusRepository.getReferenceById(EventStatusEnums.PENDING.getId()))
                .userEmail(consulta.getPaciente().getPersona().getEmail().trim())
                .ref(String.valueOf(consulta.getConsultaId()))
                .createdAt(LocalDateTime.now())
                .build());
    }

    /** El ultimo envio de cada consulta de las recetas dadas, por id de consulta. */
    private Map<Long, Event> enviosDe(Collection<Prescripcion> recetas) {
        List<String> refs = recetas.stream()
                .map(r -> String.valueOf(r.getConsulta().getConsultaId()))
                .distinct()
                .toList();
        Map<Long, Event> ultimos = new HashMap<>();
        if (refs.isEmpty()) {
            return ultimos;
        }
        // Vienen del mas reciente al mas antiguo: el primero de cada ref es el ultimo envio.
        for (Event e : eventRepository.delTipoConReferencias(EventCodeEnums.PREESCRIPTION_SEND.getId(), refs)) {
            ultimos.putIfAbsent(Long.valueOf(e.getRef()), e);
        }
        return ultimos;
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

        // Los envios de toda la pagina en una sola consulta, no uno por receta.
        Map<Long, Event> envios = enviosDe(resultado.getContent());
        return PaginaDto.de(resultado.map(r -> toDto(r, envios)));
    }

    @Transactional(readOnly = true)
    public PrescripcionResponseDto obtener(Long prescripcionId) {
        Prescripcion receta = buscarPrescripcionOFallar(prescripcionId);
        return toDto(receta, enviosDe(List.of(receta)));
    }

    /**
     * Anula una receta (HU-22, criterio 3). No la borra.
     * <p>
     * Una receta entregada es el documento con el que se despacha el
     * medicamento: no puede cambiar de contenido ni desaparecer sin dejar
     * rastro. Antes esta operacion era un DELETE fisico que se llevaba la
     * receta y sus renglones; ahora la receta pasa a ANULADA con el motivo,
     * quien y cuando, y sigue en el expediente, marcada. Para corregir una
     * receta se anula y se emite otra.
     * <p>
     * Quien puede: un ADMIN (correccion de registro) o el medico que la firmo.
     * Que cualquier medico pudiera anular la receta de otro es el mismo hueco
     * que tenia el borrado de consultas. 409 si ya estaba anulada: el rastro
     * original (motivo, autor, fecha) no se sobrescribe.
     */
    @Transactional
    public PrescripcionResponseDto anular(Long prescripcionId, String motivo) {

        Prescripcion prescripcion = buscarPrescripcionOFallar(prescripcionId);

        if (!esAdmin()) {
            Medico medico = medicoAutenticado.exigir();
            if (!prescripcion.getMedico().getPersonaId().equals(medico.getPersonaId())) {
                throw new GeneralException("Solo el médico que firmó la receta puede anularla", "403");
            }
        }

        if (prescripcion.getEstado() == EstadoPrescripcion.ANULADA) {
            throw new GeneralException("La receta ya estaba anulada", "409");
        }

        String motivoLimpio = textoONull(motivo);
        if (motivoLimpio == null || motivoLimpio.length() < 10) {
            throw new GeneralException("El motivo de la anulación debe tener al menos 10 caracteres", "400");
        }

        prescripcion.setEstado(EstadoPrescripcion.ANULADA);
        prescripcion.setMotivoAnulacion(motivoLimpio);
        prescripcion.setAnuladaEn(LocalDateTime.now());
        prescripcion.setAnuladaPor(medicoAutenticado.usuario());

        return toDto(prescripcionRepository.save(prescripcion));
    }

    /**
     * El PDF de una receta (HU-22, criterio 4): medico con su numero de junta
     * de vigilancia, datos de la clinica, paciente, y los medicamentos con
     * dosis, via, frecuencia y duracion. Una receta anulada tambien se puede
     * imprimir -el rastro es parte del expediente-, pero el PDF lo dice.
     */
    @Transactional(readOnly = true)
    public byte[] generarPdf(Long prescripcionId) {

        Prescripcion prescripcion = buscarPrescripcionOFallar(prescripcionId);
        Consulta consulta = prescripcion.getConsulta();
        Persona paciente = consulta.getPaciente().getPersona();
        Medico medico = prescripcion.getMedico();
        Clinicas clinica = consulta.getClinica();

        Map<String, Object> parametros = new HashMap<>();
        parametros.put("nombrePaciente", paciente.getNombres() + " " + paciente.getApellidos());
        parametros.put("expediente", consulta.getPaciente().getExpediente());
        parametros.put("nombreMedico", medico.getPersona().getNombres() + " " + medico.getPersona().getApellidos());
        parametros.put("registroJunta", medico.getRegistroJunta() == null || medico.getRegistroJunta().isBlank()
                ? "No registrado" : medico.getRegistroJunta());
        parametros.put("fecha", FECHA_PDF.format(prescripcion.getFecha()));
        parametros.put("fechaConsulta", FECHA_PDF.format(consulta.getFecha()));
        parametros.put("clinicaNombre", clinica == null ? "Sin clínica registrada" : clinica.getName());
        parametros.put("clinicaDireccion", clinica == null ? null : direccionDe(clinica));
        parametros.put("clinicaTelefono", clinica == null ? null : clinica.getTelefono());
        parametros.put("anulada", prescripcion.getEstado() == EstadoPrescripcion.ANULADA
                ? "RECETA ANULADA: " + prescripcion.getMotivoAnulacion() : null);

        List<PrescripcionMedicamento> renglones = prescripcion.getMedicamentos() == null
                ? List.of() : prescripcion.getMedicamentos();

        return reportService.generarPreescripcionPdf(parametros, renglones);
    }

    private static String direccionDe(Clinicas clinica) {
        return java.util.stream.Stream.of(clinica.getDireccion(), clinica.getMunicipio(), clinica.getDepartamento())
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining(", "));
    }

    private boolean esAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /**
     * HU-22, criterio 2: cada medicamento exige dosis, via de administracion,
     * frecuencia y duracion. Es 400 (el cuerpo esta incompleto) y nombra el
     * campo que falta. Va aqui y no en el DTO para que el 403 de quien no
     * puede recetar llegue antes que el 400 (ver MedicamentoRequestDto).
     */
    private void exigirTratamientoCompleto(List<MedicamentoRequestDto> renglones) {
        for (MedicamentoRequestDto r : renglones) {
            if (r == null) continue; // medicamentosRecetables lo rechaza
            if (textoONull(r.dosis()) == null) falta("la dosis");
            if (textoONull(r.viaAdministracion()) == null) falta("la vía de administración");
            if (textoONull(r.frecuencia()) == null) falta("la frecuencia");
            if (textoONull(r.duracion()) == null) falta("la duración del tratamiento");
        }
    }

    private void falta(String campo) {
        throw new GeneralException("Cada medicamento debe indicar " + campo, "400");
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

    private PrescripcionResponseDto toDto(Prescripcion prescripcion, Map<Long, Event> envios) {

        Persona personaMedico = prescripcion.getMedico().getPersona();
        Paciente paciente = prescripcion.getConsulta().getPaciente();
        Persona personaPaciente = paciente.getPersona();
        Consulta consulta = prescripcion.getConsulta();
        Clinicas clinica = consulta.getClinica();
        User anuladaPor = prescripcion.getAnuladaPor();

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
                        m.getViaAdministracion(),
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
                        personaMedico.getApellidos(),
                        prescripcion.getMedico().getRegistroJunta()
                ),
                clinica == null ? null : new ClinicaResumenDto(clinica.getClinicaId(), clinica.getName()),
                consulta.getFecha(),
                medicamentos,
                prescripcion.getJustificacionAlergia(),
                prescripcion.getEstado(),
                prescripcion.getMotivoAnulacion(),
                prescripcion.getAnuladaEn(),
                anuladaPor == null ? null
                        : anuladaPor.getPersona().getNombres() + " " + anuladaPor.getPersona().getApellidos(),
                estadoDelEnvio(prescripcion, envios),
                fechaDelEnvio(prescripcion, envios)
        );
    }

    /**
     * PENDIENTE, ENVIADO o FALLIDO segun el ultimo envio de la consulta (HU-25,
     * criterio 4). Sin envio: SIN_CORREO si el paciente no tiene correo (no hay
     * nada que reenviar, solo imprimir) o null si es una receta anterior al
     * envio automatico.
     */
    private String estadoDelEnvio(Prescripcion receta, Map<Long, Event> envios) {
        Event envio = envios.get(receta.getConsulta().getConsultaId());
        if (envio == null) {
            return tieneCorreo(receta.getConsulta()) ? null : "SIN_CORREO";
        }
        int estado = envio.getEventStatus().getEventStatusId();
        if (estado == EventStatusEnums.PROCESSED.getId()) {
            return "ENVIADO";
        }
        if (estado == EventStatusEnums.FAILED.getId()) {
            return "FALLIDO";
        }
        return "PENDIENTE";
    }

    private LocalDateTime fechaDelEnvio(Prescripcion receta, Map<Long, Event> envios) {
        Event envio = envios.get(receta.getConsulta().getConsultaId());
        if (envio == null) {
            return null;
        }
        return envio.getProcessedAt() != null ? envio.getProcessedAt() : envio.getCreatedAt();
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
