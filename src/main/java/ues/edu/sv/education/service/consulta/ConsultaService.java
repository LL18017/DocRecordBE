package ues.edu.sv.education.service.consulta;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.consulta.ClinicaResumenDto;
import ues.edu.sv.education.model.dto.consulta.ConsultaActualizacionDto;
import ues.edu.sv.education.model.dto.consulta.ConsultaRequestDto;
import ues.edu.sv.education.model.dto.consulta.ConsultaResponseDto;
import ues.edu.sv.education.model.dto.consulta.MedicoResumenDto;
import ues.edu.sv.education.model.dto.consulta.PacienteResumenDto;
import ues.edu.sv.education.model.entity.Clinicas;
import ues.edu.sv.education.model.entity.Consulta;
import ues.edu.sv.education.model.entity.Medico;
import ues.edu.sv.education.model.entity.Paciente;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.enums.EstadoConsulta;
import ues.edu.sv.education.repository.ClinicaRepository;
import ues.edu.sv.education.repository.ConsultaRepository;
import ues.edu.sv.education.repository.PacienteRepository;
import ues.edu.sv.education.service.auth.MedicoAutenticado;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Consultas medicas (epica E6).
 *
 * Dos reglas mandan sobre todo lo demas:
 *
 * 1. EL MEDICO SALE DEL TOKEN. Ni el alta ni la edicion aceptan un medicoId en
 *    el cuerpo; quien atendio es quien esta autenticado (MedicoAutenticado).
 *
 * 2. EL DIAGNOSTICO ES EXCLUSIVO DEL MEDICO. Ver escribirDiagnostico, mas
 *    abajo, para el porque de responder 403 en vez de ignorar el campo.
 */
@Service
@RequiredArgsConstructor
public class ConsultaService {

    private final ConsultaRepository consultaRepository;
    private final PacienteRepository pacienteRepository;
    private final ClinicaRepository clinicaRepository;
    private final MedicoAutenticado medicoAutenticado;

    /**
     * Registra una consulta.
     *
     * El medico no se recibe: se resuelve desde el token, y si quien pide no
     * es medico se responde 403. Por eso un ADMIN que no ejerce no puede crear
     * consultas aunque el endpoint lo deje pasar: administrar el sistema no es
     * atender pacientes, y una consulta necesita un medico de verdad al que
     * atribuirsela.
     */
    @Transactional
    public ConsultaResponseDto crear(ConsultaRequestDto request) {

        Medico medico = medicoAutenticado.exigir();
        Paciente paciente = buscarPacienteOFallar(request.pacienteId());
        Clinicas clinica = buscarClinicaSiVino(request.clinicaId());

        // Quien crea la consulta ya se comprobo que es medico, asi que puede
        // traer diagnostico desde el inicio (la consulta nace cerrada, que es
        // lo normal cuando se registra despues de atender).
        String diagnostico = textoONull(request.diagnostico());

        Consulta consulta = Consulta.builder()
                .paciente(paciente)
                .medico(medico)
                .clinica(clinica)
                .fecha(request.fecha() != null ? request.fecha() : LocalDateTime.now())
                .motivo(textoONull(request.motivo()))
                .diagnostico(diagnostico)
                .estado(estadoSegun(diagnostico))
                .build();

        return toDto(consultaRepository.save(consulta));
    }

    /**
     * El historial de un paciente, o todas las consultas si no se filtra.
     *
     * Un pacienteId que no existe responde 404 y no una lista vacia: la lista
     * vacia se lee como "este paciente no tiene consultas", que es una
     * afirmacion clinica falsa sobre alguien que ni siquiera esta registrado.
     */
    @Transactional(readOnly = true)
    public List<ConsultaResponseDto> listar(Long pacienteId) {

        if (pacienteId == null) {
            return consultaRepository.buscarTodas().stream().map(this::toDto).toList();
        }

        buscarPacienteOFallar(pacienteId);

        return consultaRepository.buscarPorPaciente(pacienteId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ConsultaResponseDto obtener(Long consultaId) {
        return toDto(buscarConsultaOFallar(consultaId));
    }

    /**
     * Actualiza una consulta sin destruir lo que ya tenia.
     *
     * Misma regla que PacienteService.actualizar: un campo que llega null -o
     * en blanco- significa "no lo estoy tocando". En un expediente clinico eso
     * importa mas que en ningun otro lado: un formulario que solo corrige la
     * sucursal no puede dejar el diagnostico en blanco.
     */
    @Transactional
    public ConsultaResponseDto actualizar(Long consultaId, ConsultaActualizacionDto request) {

        Consulta consulta = buscarConsultaOFallar(consultaId);

        if (textoONull(request.motivo()) != null) consulta.setMotivo(request.motivo().trim());
        if (request.fecha() != null) consulta.setFecha(request.fecha());
        if (request.clinicaId() != null) consulta.setClinica(buscarClinicaSiVino(request.clinicaId()));

        escribirDiagnostico(consulta, request.diagnostico());

        return toDto(consultaRepository.save(consulta));
    }

    /**
     * Escribe el diagnostico, si y solo si quien lo escribe es medico.
     *
     * ── Por que 403 y no ignorar el campo ─────────────────────────────────
     * Ignorarlo en silencio es la opcion peligrosa: quien lo escribio se va
     * convencido de que quedo registrado. En un expediente clinico, creer que
     * algo quedo escrito cuando no quedo es peor que un error en la cara -- la
     * proxima persona que abra la consulta no encuentra el diagnostico y no
     * tiene forma de saber que alguien lo intento. Se responde 403, con
     * mensaje, y no se guarda nada de la peticion.
     *
     * (Se ignora en cambio el pacienteId del cuerpo, y no es incoherente: alli
     * el cliente no esta APORTANDO informacion clinica, esta reenviando un
     * dato que ya existe. Lo que no se puede perder en silencio es lo que solo
     * existe en la peticion.)
     *
     * ── Por que la comprobacion vive aqui y no solo en @PreAuthorize ──────
     * El endpoint ya esta cerrado a ADMIN y MEDICO, asi que una enfermera
     * nunca llega. Pero "no ser enfermera" no es "ser medico": un ADMIN pasa
     * el @PreAuthorize y no ejerce la medicina. La anotacion protege la
     * puerta; esto protege el dato. Que la regla sea imposible de saltar no
     * puede depender de que nadie afloje una anotacion en el futuro.
     *
     * Un diagnostico que llega vacio no borra el que ya estaba: para eso
     * tendria que haber un acto explicito, no una omision.
     */
    private void escribirDiagnostico(Consulta consulta, String diagnostico) {

        String nuevo = textoONull(diagnostico);

        if (nuevo == null || nuevo.equals(consulta.getDiagnostico())) return;

        medicoAutenticado.exigir();

        consulta.setDiagnostico(nuevo);
        consulta.setEstado(estadoSegun(nuevo));
    }

    /**
     * Borra la consulta.
     *
     * Sus prescripciones se van con ella por el ON DELETE CASCADE de V6: una
     * receta sin la consulta que la origino es una lista de medicamentos sin
     * motivo, y eso no debe quedar en un expediente.
     */
    @Transactional
    public void eliminar(Long consultaId) {
        consultaRepository.delete(buscarConsultaOFallar(consultaId));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    /** Sin diagnostico la consulta sigue abierta; con diagnostico, cerrada. */
    private EstadoConsulta estadoSegun(String diagnostico) {
        return textoONull(diagnostico) == null ? EstadoConsulta.PENDIENTE : EstadoConsulta.FINALIZADA;
    }

    private Consulta buscarConsultaOFallar(Long consultaId) {
        return consultaRepository.buscarConDetalle(consultaId)
                .orElseThrow(() -> new NoResourceFoundException("Consulta no encontrada", "404"));
    }

    // 404 y no 500: pedir una consulta para alguien que no esta registrado
    // como paciente es pedir algo que no existe, no un fallo del servidor.
    private Paciente buscarPacienteOFallar(Long pacienteId) {
        return pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new NoResourceFoundException("Paciente no encontrado", "404"));
    }

    private Clinicas buscarClinicaSiVino(Integer clinicaId) {
        if (clinicaId == null) return null;
        return clinicaRepository.findById(clinicaId)
                .orElseThrow(() -> new NoResourceFoundException("Clínica no encontrada", "404"));
    }

    private String textoONull(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    private ConsultaResponseDto toDto(Consulta consulta) {

        Paciente paciente = consulta.getPaciente();
        Persona personaPaciente = paciente.getPersona();
        Medico medico = consulta.getMedico();
        Persona personaMedico = medico.getPersona();
        Clinicas clinica = consulta.getClinica();

        return new ConsultaResponseDto(
                consulta.getConsultaId(),
                consulta.getFecha(),
                consulta.getMotivo(),
                consulta.getDiagnostico(),
                consulta.getEstado(),
                new PacienteResumenDto(
                        paciente.getPersonaId(),
                        paciente.getExpediente(),
                        personaPaciente.getNombres(),
                        personaPaciente.getApellidos()
                ),
                new MedicoResumenDto(
                        medico.getPersonaId(),
                        personaMedico.getNombres(),
                        personaMedico.getApellidos(),
                        medico.getEspecialidad() != null ? medico.getEspecialidad().getNombre() : null
                ),
                clinica == null ? null : new ClinicaResumenDto(clinica.getClinicaId(), clinica.getName())
        );
    }
}
