package ues.edu.sv.education.service.anulacion;

import ues.edu.sv.education.service.paciente.ExpedientePropio;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.anulacion.ParametroDeAnulacionDto;
import ues.edu.sv.education.model.dto.anulacion.ParametroDeAnulacionRequestDto;
import ues.edu.sv.education.model.dto.anulacion.SolicitudDeAnulacionDto;
import ues.edu.sv.education.model.dto.consulta.PacienteResumenDto;
import ues.edu.sv.education.model.entity.*;
import ues.edu.sv.education.model.enums.EstadoConsulta;
import ues.edu.sv.education.model.enums.EstadoPrescripcion;
import ues.edu.sv.education.model.enums.EstadoSolicitudDeAnulacion;
import ues.edu.sv.education.model.enums.TipoDocumentoAnulable;
import ues.edu.sv.education.repository.*;
import ues.edu.sv.education.service.auth.MedicoAutenticado;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Anulacion de consultas (HU-21) y recetas (HU-22) con ventana y aprobacion.
 *
 * ── La regla ──────────────────────────────────────────────────────────────
 * Anula quien firmo el documento o un administrador, siempre con motivo. Lo
 * que cambia es CUANDO basta con eso:
 *
 *   · Dentro de la ventana (parametros_de_anulacion.minutos_anulacion_directa,
 *     contados desde que el documento se registro) se anula al momento. Es el
 *     error que se nota enseguida: el paciente equivocado, la dosis mal
 *     tecleada, y nadie lo ha usado todavia.
 *   · Pasada la ventana, la anulacion queda como SOLICITUD y el documento
 *     sigue vigente hasta que la aprueben tantos administradores como pida el
 *     parametro, todos distintos de quien la pidio. Para entonces la receta ya
 *     pudo despacharse y la consulta leerse; deshacerlo es una decision que no
 *     toma una sola persona.
 *   · Pasado el tope de dias, si lo hay, ya no se anula (409).
 *
 * ── Por que vive aqui y no en cada servicio ───────────────────────────────
 * Es la misma regla para los dos documentos, y anular una consulta anula sus
 * recetas (HU-21 c4): las dos anulaciones se tocan. Repartida entre
 * ConsultaService y PrescripcionService, cada una tendria su propia version
 * de la ventana y del conteo de aprobaciones, y con el tiempo dejarian de
 * coincidir, que es justo lo que el analisis de coherencia encontro en las
 * anulaciones de antes.
 *
 * ── Quien queda como autor ────────────────────────────────────────────────
 * anulada_por es quien PIDIO la anulacion, tambien cuando se aplica por
 * aprobacion: es quien sostiene el motivo. Los administradores que aprobaron
 * quedan en aprobaciones_de_anulacion, con su hora.
 */
@Service
@RequiredArgsConstructor
public class AnulacionService {

    private static final int MOTIVO_MINIMO = 10;
    private static final int MOTIVO_MAXIMO = 500;

    private final ConsultaRepository consultaRepository;
    private final PrescripcionRepository prescripcionRepository;
    private final SolicitudDeAnulacionRepository solicitudRepository;
    private final AprobacionDeAnulacionRepository aprobacionRepository;
    private final ParametroDeAnulacionRepository parametroRepository;
    private final MedicoAutenticado medicoAutenticado;
    private final ExpedientePropio expedientePropio;

    /**
     * Lo que paso al pedir una anulacion: o el documento ya quedo anulado
     * (solicitud null), o quedo una solicitud esperando aprobacion.
     */
    public record Resultado(SolicitudDeAnulacionDto solicitud) {
        public boolean anulado() {
            return solicitud == null;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Pedir la anulacion
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Anula una consulta, o pide anularla si ya paso la ventana (HU-21).
     *
     * Orden de las comprobaciones: existe (404), quien la pide puede (403), no
     * esta ya anulada (409), el motivo vale (400), no hay otra solicitud en
     * curso (409), no paso el tope (409). Quien no tiene permiso se entera de
     * eso antes que del estado del documento.
     */
    @Transactional
    public Resultado anularConsulta(Long consultaId, String motivo) {

        Consulta consulta = consultaRepository.buscarParaAnular(consultaId)
                .orElseThrow(() -> new NoResourceFoundException("Consulta no encontrada", "404"));
        expedientePropio.exigirAjeno(consulta.getPaciente().getPersonaId());

        exigirFirmanteOAdmin(consulta.getMedico(), "Solo el médico que registró la consulta o un administrador puede anularla");

        if (consulta.getEstado() == EstadoConsulta.ANULADA) {
            throw new GeneralException("La consulta ya estaba anulada", "409");
        }
        String motivoLimpio = exigirMotivo(motivo, "El motivo de la anulación");

        if (solicitudRepository.pendienteDeConsulta(consultaId).isPresent()) {
            throw new GeneralException(
                    "Ya hay una solicitud de anulación en curso para esta consulta", "409");
        }

        User usuario = medicoAutenticado.usuario();
        ParametroDeAnulacion parametro = parametroDe(TipoDocumentoAnulable.CONSULTA);

        if (dentroDeLaVentana(consulta.getRegistradaEn(), parametro, "La consulta")) {
            aplicarAnulacionDeConsulta(consulta, motivoLimpio, usuario, LocalDateTime.now());
            return new Resultado(null);
        }

        SolicitudDeAnulacion solicitud = solicitudRepository.save(SolicitudDeAnulacion.builder()
                .tipo(TipoDocumentoAnulable.CONSULTA)
                .consulta(consulta)
                .motivo(motivoLimpio)
                .solicitadaPor(usuario)
                .solicitadaEn(LocalDateTime.now())
                .aprobacionesRequeridas(parametro.getAprobacionesRequeridas())
                .build());
        return new Resultado(toDto(solicitud));
    }

    /** Anula una receta, o pide anularla si ya paso la ventana (HU-22 c3). */
    @Transactional
    public Resultado anularReceta(Long prescripcionId, String motivo) {

        Prescripcion receta = prescripcionRepository.buscarParaAnular(prescripcionId)
                .orElseThrow(() -> new NoResourceFoundException("Prescripcion no encontrada", "404"));
        expedientePropio.exigirAjeno(receta.getConsulta().getPaciente().getPersonaId());

        exigirFirmanteOAdmin(receta.getMedico(), "Solo el médico que firmó la receta puede anularla");

        // Una receta de una consulta anulada ya esta anulada (HU-21 c4), asi
        // que esta comprobacion tambien cubre ese caso.
        if (receta.getEstado() == EstadoPrescripcion.ANULADA) {
            throw new GeneralException("La receta ya estaba anulada", "409");
        }
        String motivoLimpio = exigirMotivo(motivo, "El motivo de la anulación");

        if (solicitudRepository.pendienteDeReceta(prescripcionId).isPresent()) {
            throw new GeneralException(
                    "Ya hay una solicitud de anulación en curso para esta receta", "409");
        }

        User usuario = medicoAutenticado.usuario();
        ParametroDeAnulacion parametro = parametroDe(TipoDocumentoAnulable.RECETA);

        if (dentroDeLaVentana(receta.getFecha(), parametro, "La receta")) {
            aplicarAnulacionDeReceta(receta, motivoLimpio, usuario, LocalDateTime.now());
            return new Resultado(null);
        }

        SolicitudDeAnulacion solicitud = solicitudRepository.save(SolicitudDeAnulacion.builder()
                .tipo(TipoDocumentoAnulable.RECETA)
                .consulta(receta.getConsulta())
                .prescripcion(receta)
                .motivo(motivoLimpio)
                .solicitadaPor(usuario)
                .solicitadaEn(LocalDateTime.now())
                .aprobacionesRequeridas(parametro.getAprobacionesRequeridas())
                .build());
        return new Resultado(toDto(solicitud));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Resolver la solicitud
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Un administrador aprueba la solicitud. Con la ultima aprobacion que
     * faltaba, el documento se anula en la misma transaccion.
     *
     * 403 si quien aprueba no es administrador o es quien la pidio: pedirla y
     * aprobarla la misma persona es justo lo que la aprobacion existe para
     * impedir. 409 si ya la aprobo o si la solicitud ya se resolvio.
     */
    @Transactional
    public SolicitudDeAnulacionDto aprobar(Long solicitudId) {

        SolicitudDeAnulacion solicitud = solicitudParaResolver(solicitudId);
        User admin = medicoAutenticado.usuario();

        if (aprobacionRepository.yaAprobo(solicitudId, admin.getUserID())) {
            throw new GeneralException("Ya aprobó esta solicitud", "409");
        }

        LocalDateTime ahora = LocalDateTime.now();
        aprobacionRepository.saveAndFlush(AprobacionDeAnulacion.builder()
                .solicitud(solicitud)
                .usuario(admin)
                .aprobadaEn(ahora)
                .build());

        long aprobaciones = aprobacionRepository.countBySolicitud_SolicitudId(solicitudId);
        if (aprobaciones >= solicitud.getAprobacionesRequeridas()) {
            aplicar(solicitud, ahora);
            solicitud.setEstado(EstadoSolicitudDeAnulacion.APROBADA);
            solicitud.setResueltaEn(ahora);
            solicitud.setResueltaPor(admin);
            solicitudRepository.save(solicitud);
        }

        return toDto(recargar(solicitudId));
    }

    /**
     * Un administrador rechaza la solicitud. Basta uno: la anulacion pide
     * acuerdo, y un rechazo dice que no lo hay. El documento sigue vigente y se
     * puede pedir de nuevo con mejores razones.
     */
    @Transactional
    public SolicitudDeAnulacionDto rechazar(Long solicitudId, String motivo) {

        SolicitudDeAnulacion solicitud = solicitudParaResolver(solicitudId);
        String motivoLimpio = exigirMotivo(motivo, "El motivo del rechazo");

        solicitud.setEstado(EstadoSolicitudDeAnulacion.RECHAZADA);
        solicitud.setMotivoRechazo(motivoLimpio);
        solicitud.setResueltaEn(LocalDateTime.now());
        solicitud.setResueltaPor(medicoAutenticado.usuario());
        solicitudRepository.save(solicitud);

        return toDto(recargar(solicitudId));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Consultar
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Las solicitudes, de la mas reciente a la mas antigua.
     *
     * Un administrador ve todas. Un medico ve las que pidio el, o -con
     * consultaId- las de una consulta, que es lo que necesita la pantalla de
     * la consulta para mostrar "anulacion pendiente": el medico ya puede leer
     * la consulta entera, asi que saber que alguien pidio anularla no le
     * descubre nada nuevo.
     */
    @Transactional(readOnly = true)
    public List<SolicitudDeAnulacionDto> listar(EstadoSolicitudDeAnulacion estado, Long consultaId) {

        Integer soloDe = esAdmin() || consultaId != null ? null : medicoAutenticado.usuario().getUserID();

        return solicitudRepository.buscar(estado, soloDe).stream()
                .filter(s -> consultaId == null || s.getConsulta().getConsultaId().equals(consultaId))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public SolicitudDeAnulacionDto obtener(Long solicitudId) {
        SolicitudDeAnulacion solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new NoResourceFoundException("Solicitud de anulación no encontrada", "404"));

        if (!esAdmin() && !solicitud.getSolicitadaPor().getUserID().equals(medicoAutenticado.usuario().getUserID())) {
            throw new GeneralException("Solo quien pidió la anulación o un administrador puede verla", "403");
        }
        return toDto(solicitud);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Parametros
    // ══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<ParametroDeAnulacionDto> parametros() {
        return Arrays.stream(TipoDocumentoAnulable.values())
                .map(this::parametroDe)
                .map(this::toDto)
                .toList();
    }

    /**
     * Cambia las reglas de un tipo de documento. No toca las solicitudes en
     * curso: cada una guardo cuantas aprobaciones necesitaba al pedirse.
     */
    @Transactional
    public ParametroDeAnulacionDto actualizarParametro(TipoDocumentoAnulable tipo, ParametroDeAnulacionRequestDto request) {

        ParametroDeAnulacion parametro = parametroDe(tipo);
        parametro.setMinutosAnulacionDirecta(request.minutosAnulacionDirecta());
        parametro.setAprobacionesRequeridas(request.aprobacionesRequeridas());
        parametro.setDiasMaximos(request.diasMaximos());
        parametro.setActualizadoEn(LocalDateTime.now());
        parametro.setActualizadoPor(medicoAutenticado.usuario());

        return toDto(parametroRepository.save(parametro));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Aplicar la anulacion
    // ══════════════════════════════════════════════════════════════════════

    private void aplicar(SolicitudDeAnulacion solicitud, LocalDateTime ahora) {
        if (solicitud.getTipo() == TipoDocumentoAnulable.CONSULTA) {
            Consulta consulta = consultaRepository.buscarParaAnular(solicitud.getConsulta().getConsultaId())
                    .orElseThrow(() -> new NoResourceFoundException("Consulta no encontrada", "404"));
            aplicarAnulacionDeConsulta(consulta, solicitud.getMotivo(), solicitud.getSolicitadaPor(), ahora);
        } else {
            Prescripcion receta = prescripcionRepository.buscarParaAnular(solicitud.getPrescripcion().getPrescripcionId())
                    .orElseThrow(() -> new NoResourceFoundException("Prescripcion no encontrada", "404"));
            aplicarAnulacionDeReceta(receta, solicitud.getMotivo(), solicitud.getSolicitadaPor(), ahora);
        }
    }

    /**
     * La consulta queda ANULADA y, con ella, sus recetas vigentes (HU-21 c4):
     * una receta de una atencion que no debio registrarse no puede seguir
     * sirviendo para despachar. Las solicitudes en curso de esas recetas se
     * cancelan, porque ya no hay nada que decidir.
     */
    private void aplicarAnulacionDeConsulta(Consulta consulta, String motivo, User autor, LocalDateTime ahora) {

        consulta.setEstado(EstadoConsulta.ANULADA);
        consulta.setMotivoAnulacion(motivo);
        consulta.setAnuladaEn(ahora);
        consulta.setAnuladaPor(autor);
        consultaRepository.save(consulta);

        String motivoDeLasRecetas = recortar("Anulada con su consulta: " + motivo);
        for (Prescripcion receta : prescripcionRepository.buscarPorConsulta(consulta.getConsultaId())) {
            aplicarAnulacionDeReceta(receta, motivoDeLasRecetas, autor, ahora);
        }

        for (SolicitudDeAnulacion pendiente : solicitudRepository.pendientesDeRecetasDe(consulta.getConsultaId())) {
            pendiente.setEstado(EstadoSolicitudDeAnulacion.CANCELADA);
            pendiente.setResueltaEn(ahora);
            solicitudRepository.save(pendiente);
        }
    }

    private void aplicarAnulacionDeReceta(Prescripcion receta, String motivo, User autor, LocalDateTime ahora) {
        receta.setEstado(EstadoPrescripcion.ANULADA);
        receta.setMotivoAnulacion(motivo);
        receta.setAnuladaEn(ahora);
        receta.setAnuladaPor(autor);
        prescripcionRepository.save(receta);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Si el documento todavia se anula directo. 409 si ya paso el tope de dias.
     *
     * minutos = 0 apaga la anulacion directa: todo pasa por aprobacion.
     */
    private boolean dentroDeLaVentana(LocalDateTime registrado, ParametroDeAnulacion parametro, String documento) {

        Duration edad = Duration.between(registrado, LocalDateTime.now());

        if (parametro.getDiasMaximos() != null && edad.compareTo(Duration.ofDays(parametro.getDiasMaximos())) > 0) {
            throw new GeneralException(documento + " tiene más de " + parametro.getDiasMaximos()
                    + " días y ya no se puede anular", "409");
        }

        int minutos = parametro.getMinutosAnulacionDirecta();
        return minutos > 0 && edad.compareTo(Duration.ofMinutes(minutos)) <= 0;
    }

    /**
     * Quien pide la anulacion firmo el documento, o es administrador. La firma
     * se compara por persona, como en el resto del dominio clinico.
     */
    private void exigirFirmanteOAdmin(Medico firmante, String mensaje) {
        if (esAdmin()) return;
        Medico medico = medicoAutenticado.exigir();
        if (!firmante.getPersonaId().equals(medico.getPersonaId())) {
            throw new GeneralException(mensaje, "403");
        }
    }

    /**
     * La solicitud, bloqueada y lista para resolver: existe, sigue pendiente y
     * quien la resuelve es un administrador distinto de quien la pidio.
     */
    private SolicitudDeAnulacion solicitudParaResolver(Long solicitudId) {

        if (!esAdmin()) {
            throw new GeneralException("Solo un administrador puede aprobar o rechazar una anulación", "403");
        }

        SolicitudDeAnulacion solicitud = solicitudRepository.buscarParaResolver(solicitudId)
                .orElseThrow(() -> new NoResourceFoundException("Solicitud de anulación no encontrada", "404"));

        if (solicitud.getSolicitadaPor().getUserID().equals(medicoAutenticado.usuario().getUserID())) {
            throw new GeneralException("No puede resolver una anulación que pidió usted", "403");
        }
        // Tampoco la de un documento de su propio expediente: la pudo pedir
        // otro, pero resolverla seria decidir sobre lo suyo.
        expedientePropio.exigirAjenoParaResolver(solicitud.getConsulta().getPaciente().getPersonaId());
        if (solicitud.getEstado() != EstadoSolicitudDeAnulacion.PENDIENTE) {
            throw new GeneralException("La solicitud ya fue resuelta", "409");
        }
        return solicitud;
    }

    private String exigirMotivo(String motivo, String nombre) {
        String limpio = motivo == null ? "" : motivo.trim();
        if (limpio.length() < MOTIVO_MINIMO || limpio.length() > MOTIVO_MAXIMO) {
            throw new GeneralException(nombre + " debe tener entre " + MOTIVO_MINIMO + " y "
                    + MOTIVO_MAXIMO + " caracteres", "400");
        }
        return limpio;
    }

    private String recortar(String texto) {
        return texto.length() <= MOTIVO_MAXIMO ? texto : texto.substring(0, MOTIVO_MAXIMO);
    }

    private ParametroDeAnulacion parametroDe(TipoDocumentoAnulable tipo) {
        // V35 siembra las dos filas; si faltara una, es la base la que esta
        // mal, no la peticion.
        return parametroRepository.findById(tipo).orElseThrow(() -> new IllegalStateException(
                "Falta la fila de parametros_de_anulacion para " + tipo));
    }

    private SolicitudDeAnulacion recargar(Long solicitudId) {
        return solicitudRepository.findById(solicitudId).orElseThrow();
    }

    private boolean esAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private static String nombre(User usuario) {
        if (usuario == null) return null;
        Persona p = usuario.getPersona();
        return p == null ? usuario.getEmail() : p.getNombres() + " " + p.getApellidos();
    }

    private SolicitudDeAnulacionDto toDto(SolicitudDeAnulacion s) {

        Paciente paciente = s.getConsulta().getPaciente();
        Persona persona = paciente.getPersona();
        List<AprobacionDeAnulacion> aprobaciones =
                aprobacionRepository.findBySolicitud_SolicitudIdOrderByAprobadaEnAsc(s.getSolicitudId());

        Optional<Integer> yo = usuarioActualSiHay();
        boolean puedeResolver = s.getEstado() == EstadoSolicitudDeAnulacion.PENDIENTE
                && esAdmin()
                && yo.isPresent()
                && !yo.get().equals(s.getSolicitadaPor().getUserID())
                && aprobaciones.stream().noneMatch(a -> a.getUsuario().getUserID().equals(yo.get()));

        return new SolicitudDeAnulacionDto(
                s.getSolicitudId(),
                s.getTipo(),
                s.getConsulta().getConsultaId(),
                s.getPrescripcion() == null ? null : s.getPrescripcion().getPrescripcionId(),
                new PacienteResumenDto(paciente.getPersonaId(), paciente.getExpediente(),
                        persona.getNombres(), persona.getApellidos()),
                s.getMotivo(),
                nombre(s.getSolicitadaPor()),
                s.getSolicitadaEn(),
                s.getAprobacionesRequeridas(),
                aprobaciones.stream()
                        .map(a -> new SolicitudDeAnulacionDto.AprobacionDto(nombre(a.getUsuario()), a.getAprobadaEn()))
                        .toList(),
                s.getEstado(),
                s.getResueltaEn(),
                nombre(s.getResueltaPor()),
                s.getMotivoRechazo(),
                puedeResolver
        );
    }

    private ParametroDeAnulacionDto toDto(ParametroDeAnulacion p) {
        return new ParametroDeAnulacionDto(
                p.getTipo(),
                p.getMinutosAnulacionDirecta(),
                p.getAprobacionesRequeridas(),
                p.getDiasMaximos(),
                p.getActualizadoEn(),
                nombre(p.getActualizadoPor()));
    }

    private Optional<Integer> usuarioActualSiHay() {
        try {
            return Optional.of(medicoAutenticado.usuario().getUserID());
        } catch (GeneralException e) {
            return Optional.empty();
        }
    }
}
