package ues.edu.sv.education.service.portal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.model.dto.Alergia.AlergiaResponse;
import ues.edu.sv.education.model.dto.cita.CitaResponseDto;
import ues.edu.sv.education.model.dto.consulta.ConsultaResponseDto;
import ues.edu.sv.education.model.dto.consulta.MedicoResumenDto;
import ues.edu.sv.education.model.dto.portal.MiExpedienteDto;
import ues.edu.sv.education.model.dto.portal.TerminoDelGlosarioDto;
import ues.edu.sv.education.model.dto.prescripcion.MedicamentoResponseDto;
import ues.edu.sv.education.model.dto.prescripcion.PrescripcionResponseDto;
import ues.edu.sv.education.model.dto.signosvitales.SignosVitalesResponseDto;
import ues.edu.sv.education.model.entity.AccesoAlExpediente;
import ues.edu.sv.education.model.entity.Paciente;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.enums.EstadoConsulta;
import ues.edu.sv.education.model.enums.EstadoPrescripcion;
import ues.edu.sv.education.model.enums.SeveridadDeAlergia;
import ues.edu.sv.education.service.Alergia.AlergiaService;
import ues.edu.sv.education.service.Cita.CitaService;
import ues.edu.sv.education.service.ReportService;
import ues.edu.sv.education.service.auth.PacienteAutenticado;
import ues.edu.sv.education.service.consulta.ConsultaService;
import ues.edu.sv.education.service.prescripcion.PrescripcionService;
import ues.edu.sv.education.service.signosvitales.SignosVitalesService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * El portal del paciente (HU-34): su expediente, en solo lectura, en pantalla
 * y en PDF.
 *
 * ── El paciente sale del token ────────────────────────────────────────────
 * Ningun metodo de aqui recibe un id. El expediente que se arma es siempre el
 * de la cuenta que inicio sesion (PacienteAutenticado), asi que pedir el de
 * otro no es "un id que hay que validar": es una pregunta que esta API no sabe
 * hacer (criterio 2). Los endpoints clinicos que si reciben un id (/pacientes,
 * /consultas, /alergias...) siguen cerrados al rol PACIENTE.
 *
 * ── Solo lectura ──────────────────────────────────────────────────────────
 * Criterio 4. No hay un solo metodo que escriba en el expediente: el portal
 * reutiliza las lecturas de los servicios clinicos y nada mas. Lo unico que se
 * escribe es la huella del acceso (criterio 6), que no es dato clinico.
 *
 * ── Reutiliza, no duplica ─────────────────────────────────────────────────
 * Cada seccion la arma el servicio que ya la arma para el personal clinico,
 * con sus mismas reglas de orden y de estado (las alergias severas primero,
 * las consultas y recetas anuladas visibles y marcadas). Lo unico propio del
 * portal son dos lecturas "del propio paciente" en ConsultaService y
 * PrescripcionService, que ven el porque en su javadoc.
 */
@Service
@RequiredArgsConstructor
public class PortalDelPacienteService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_Y_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PacienteAutenticado pacienteAutenticado;
    private final AlergiaService alergiaService;
    private final ConsultaService consultaService;
    private final PrescripcionService prescripcionService;
    private final CitaService citaService;
    private final SignosVitalesService signosVitalesService;
    private final ReportService reportService;
    private final BitacoraDeAccesos bitacora;

    /**
     * El expediente del paciente de la sesion, y la huella de que lo abrio.
     *
     * Transaccion de escritura y no readOnly por la bitacora: la fila de
     * acceso se confirma junto con la lectura, o no se confirma.
     */
    @Transactional
    public MiExpedienteDto miExpediente() {
        PacienteAutenticado.EnSesion sesion = pacienteAutenticado.exigir();
        MiExpedienteDto expediente = armar(sesion.paciente());
        bitacora.registrar(sesion.paciente().getPersonaId(), sesion.cuenta(), AccesoAlExpediente.VISTA);
        return expediente;
    }

    /**
     * El mismo expediente en PDF (criterio 5), con el glosario al final para
     * que el papel tambien explique lo que dice (criterio 3). Deja su propia
     * huella, de tipo PDF: un expediente descargado sale del sistema, y eso es
     * justo lo que una auditoria quiere distinguir de "lo miro en pantalla".
     */
    @Transactional
    public ExpedienteEnPdf miExpedienteEnPdf() {
        PacienteAutenticado.EnSesion sesion = pacienteAutenticado.exigir();
        MiExpedienteDto expediente = armar(sesion.paciente());

        MiExpedienteDto.DatosDelPacienteDto paciente = expediente.paciente();
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("nombrePaciente", paciente.nombres() + " " + paciente.apellidos());
        parametros.put("expediente", paciente.expediente());
        parametros.put("datosPersonales", datosPersonalesEnTexto(paciente));
        parametros.put("generadoEn", FECHA_Y_HORA.format(expediente.consultadoEn()));

        byte[] pdf = reportService.generarExpedientePdf(parametros, renglonesDe(expediente));

        // Despues de generar el PDF y no antes: si la plantilla falla, el
        // paciente no se llevo nada y la bitacora no debe decir lo contrario.
        bitacora.registrar(sesion.paciente().getPersonaId(), sesion.cuenta(), AccesoAlExpediente.PDF);
        return new ExpedienteEnPdf(paciente.expediente(), pdf);
    }

    /** El PDF y el numero de expediente, que da nombre al archivo. */
    public record ExpedienteEnPdf(String expediente, byte[] contenido) {
    }

    // ══════════════════════════════════════════════════════════════════════
    // Armado
    // ══════════════════════════════════════════════════════════════════════

    private MiExpedienteDto armar(Paciente paciente) {
        Long id = paciente.getPersonaId();
        Persona persona = paciente.getPersona();

        return new MiExpedienteDto(
                new MiExpedienteDto.DatosDelPacienteDto(
                        id,
                        paciente.getExpediente(),
                        persona.getNombres(),
                        persona.getApellidos(),
                        persona.getDui(),
                        persona.getFechaNacimiento(),
                        persona.getSexo(),
                        paciente.getTipoSangre(),
                        persona.getTelefono(),
                        persona.getDireccion(),
                        persona.getEmail()),
                alergiaService.listarDelPaciente(id),
                signosVitalesService.ultimaDelPaciente(id),
                consultaService.delPropioPaciente(id),
                prescripcionService.todasDelPaciente(id),
                citaService.proximasDelPaciente(id),
                GlosarioClinico.TERMINOS,
                LocalDateTime.now());
    }

    // ══════════════════════════════════════════════════════════════════════
    // El expediente en renglones, para el PDF
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Un renglon del PDF. Clase con getters y no record porque Jasper lee los
     * campos de la fuente de datos como propiedades JavaBean (getSeccion...),
     * y un record no las tiene.
     */
    @Getter
    @AllArgsConstructor
    public static class RenglonDelExpediente {
        private final String seccion;
        private final String titulo;
        private final String detalle;
        /** Texto en rojo junto al titulo (ANULADA, ALERGIA SEVERA); null si no hay nada que advertir. */
        private final String marca;
    }

    /**
     * Las secciones en el orden del documento. Una seccion vacia lleva un
     * renglon que lo dice: un PDF sin la seccion de alergias no distingue
     * "no tiene alergias" de "se olvido imprimirlas", y en un documento
     * clinico esa diferencia importa.
     */
    List<RenglonDelExpediente> renglonesDe(MiExpedienteDto expediente) {
        List<RenglonDelExpediente> renglones = new ArrayList<>();

        String alergias = "Alergias";
        if (expediente.alergias().isEmpty()) {
            renglones.add(new RenglonDelExpediente(alergias, "No tienes alergias registradas.", null, null));
        }
        for (AlergiaResponse a : expediente.alergias()) {
            renglones.add(new RenglonDelExpediente(alergias,
                    a.sustancia() + " · severidad " + a.severidad().name().toLowerCase(),
                    "Reacción: " + a.reaccion()
                            + (a.fechaDeteccion() == null ? "" : " · Detectada el " + FECHA.format(a.fechaDeteccion())),
                    a.severidad() == SeveridadDeAlergia.SEVERA ? "ALERGIA SEVERA" : null));
        }

        String signos = "Últimos signos vitales";
        SignosVitalesResponseDto toma = expediente.ultimosSignosVitales();
        if (toma == null) {
            renglones.add(new RenglonDelExpediente(signos, "Todavía no se te han tomado signos vitales.", null, null));
        } else {
            renglones.add(new RenglonDelExpediente(signos,
                    "Tomados el " + FECHA_Y_HORA.format(toma.tomadoEn()),
                    medidasDe(toma),
                    null));
        }

        String consultas = "Consultas";
        if (expediente.consultas().isEmpty()) {
            renglones.add(new RenglonDelExpediente(consultas, "No tienes consultas registradas.", null, null));
        }
        for (ConsultaResponseDto c : expediente.consultas()) {
            boolean anulada = c.estado() == EstadoConsulta.ANULADA;
            renglones.add(new RenglonDelExpediente(consultas,
                    FECHA_Y_HORA.format(c.fecha()) + " · " + nombreDe(c.medico())
                            + (c.clinica() == null ? "" : " · " + c.clinica().name()),
                    lineas(
                            "Motivo de consulta: " + c.motivo(),
                            c.diagnostico() == null ? "Diagnóstico: pendiente" : "Diagnóstico: " + c.diagnostico(),
                            c.exploracionFisica() == null ? null : "Exploración física: " + c.exploracionFisica(),
                            c.indicaciones() == null ? null : "Indicaciones: " + c.indicaciones(),
                            anulada ? "Anulada: " + c.motivoAnulacion() : null),
                    anulada ? "ANULADA" : null));
        }

        String recetas = "Recetas";
        if (expediente.recetas().isEmpty()) {
            renglones.add(new RenglonDelExpediente(recetas, "No tienes recetas registradas.", null, null));
        }
        for (PrescripcionResponseDto r : expediente.recetas()) {
            boolean anulada = r.estado() == EstadoPrescripcion.ANULADA;
            List<String> detalle = new ArrayList<>();
            for (MedicamentoResponseDto m : r.medicamentos()) {
                detalle.add("• " + m.medicamento() + ": " + m.dosis() + ", vía " + m.viaAdministracion()
                        + ", " + m.frecuencia() + ", durante " + m.duracion());
            }
            if (anulada) detalle.add("Anulada: " + r.motivoAnulacion());
            renglones.add(new RenglonDelExpediente(recetas,
                    "Receta #" + r.prescripcionId() + " · " + FECHA_Y_HORA.format(r.fecha())
                            + " · " + r.medico().nombres() + " " + r.medico().apellidos(),
                    String.join("\n", detalle),
                    anulada ? "ANULADA" : null));
        }

        String citas = "Próximas citas";
        if (expediente.proximasCitas().isEmpty()) {
            renglones.add(new RenglonDelExpediente(citas, "No tienes citas próximas.", null, null));
        }
        for (CitaResponseDto c : expediente.proximasCitas()) {
            renglones.add(new RenglonDelExpediente(citas,
                    FECHA_Y_HORA.format(c.fechaHora()) + (c.tipoCita() == null ? "" : " · " + c.tipoCita()),
                    lineas(
                            c.medico() == null ? null : "Con: " + c.medico(),
                            c.clinica() == null ? null : "Clínica: " + c.clinica()),
                    null));
        }

        for (TerminoDelGlosarioDto t : expediente.glosario()) {
            renglones.add(new RenglonDelExpediente("Glosario: qué significa cada término",
                    t.termino(), t.explicacion(), null));
        }
        return renglones;
    }

    private static String datosPersonalesEnTexto(MiExpedienteDto.DatosDelPacienteDto p) {
        return lineas(
                p.dui() == null ? null : "DUI: " + p.dui(),
                p.fechaNacimiento() == null ? null : "Fecha de nacimiento: " + FECHA.format(p.fechaNacimiento()),
                p.sexo() == null ? null : "Sexo: " + ("F".equals(p.sexo()) ? "femenino" : "masculino"),
                "Tipo de sangre: " + (p.tipoSangre() == null ? "no registrado" : p.tipoSangre()));
    }

    /** Las medidas que se tomaron, con su unidad. Las que no se tomaron no salen. */
    private static String medidasDe(SignosVitalesResponseDto t) {
        String presion = t.presionSistolica() == null || t.presionDiastolica() == null
                ? null
                : "Presión arterial: " + t.presionSistolica() + "/" + t.presionDiastolica() + " mmHg";
        return lineas(
                presion,
                t.pulsoLpm() == null ? null : "Frecuencia cardiaca: " + t.pulsoLpm() + " lpm",
                t.frecuenciaRespRpm() == null ? null : "Frecuencia respiratoria: " + t.frecuenciaRespRpm() + " rpm",
                t.temperaturaC() == null ? null : "Temperatura: " + t.temperaturaC() + " °C",
                t.saturacionPct() == null ? null : "Saturación de oxígeno: " + t.saturacionPct() + " %",
                t.pesoKg() == null ? null : "Peso: " + t.pesoKg() + " kg",
                t.estaturaCm() == null ? null : "Estatura: " + t.estaturaCm() + " cm",
                t.imc() == null ? null : "Índice de masa corporal (IMC): " + t.imc()
                        + (t.clasificacionImc() == null ? "" : " (" + t.clasificacionImc() + ")"));
    }

    private static String nombreDe(MedicoResumenDto medico) {
        return medico.nombres() + " " + medico.apellidos()
                + (medico.especialidad() == null ? "" : " (" + medico.especialidad() + ")");
    }

    /** Une las lineas que existen; las null se saltan. */
    private static String lineas(String... lineas) {
        return Stream.of(lineas).filter(Objects::nonNull).collect(Collectors.joining("\n"));
    }
}
