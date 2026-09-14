package ues.edu.sv.education.service.signosvitales;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.common.PaginaDto;
import ues.edu.sv.education.model.dto.consulta.PacienteResumenDto;
import ues.edu.sv.education.model.dto.signosvitales.EnfermeraTomaDto;
import ues.edu.sv.education.model.dto.signosvitales.SignosVitalesRequestDto;
import ues.edu.sv.education.model.dto.signosvitales.SignosVitalesResponseDto;
import ues.edu.sv.education.model.entity.Consulta;
import ues.edu.sv.education.model.entity.Enfermera;
import ues.edu.sv.education.model.entity.Paciente;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.SignosVitales;
import ues.edu.sv.education.repository.ConsultaRepository;
import ues.edu.sv.education.repository.PacienteRepository;
import ues.edu.sv.education.repository.SignosVitalesRepository;
import ues.edu.sv.education.service.auth.EnfermeraAutenticado;

import java.time.LocalDateTime;

/**
 * Signos vitales: el triage que precede a la consulta.
 *
 * ── La regla que da forma a todo esto ──────────────────────────────────────
 * Enfermeria REGISTRA, el medico LEE. No es una restriccion administrativa:
 * es como funciona una consulta. Quien pesa, mide y toma la presion no es
 * quien diagnostica, y el medico necesita ese dato ANTES de diagnosticar.
 *
 * De ahi que crear() resuelva a la enfermera desde el token y no acepte un id
 * en el cuerpo -misma defensa que ConsultaService con el medico-, mientras
 * que las lecturas esten abiertas a MEDICO y ADMIN ademas de ENFERMERA.
 *
 * ── Por que no se bloquea la consulta sin constantes ──────────────────────
 * Seria tentador exigir una toma reciente para poder abrir una consulta, ya
 * que ese es el orden correcto. No se hace, y conviene dejar dicho por que:
 * en una urgencia el medico atiende primero y el papeleo va despues, y un
 * sistema que se lo impida termina enseñando al personal a inventar una toma
 * para desbloquear la pantalla. Un dato falso es peor que un dato ausente
 * -mismo principio que el frontend aplica al mostrar el hueco honesto. El
 * sistema deja el registro a la vista y quien audita ve la falta; no finge
 * impedirla.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignosVitalesService {

    private static final int TAMANO_PAGINA_POR_DEFECTO = 20;
    private static final int TAMANO_PAGINA_MAXIMO = 100;

    private final SignosVitalesRepository signosVitalesRepository;
    private final PacienteRepository pacienteRepository;
    private final ConsultaRepository consultaRepository;
    private final EnfermeraAutenticado enfermeraAutenticado;

    /**
     * Registra una toma. La firma la enfermera autenticada.
     *
     * 403 si quien opera no tiene fila en `enfermeras` (un medico entra aqui,
     * y debe entrar); 404 si el paciente -o la consulta, cuando se manda- no
     * existe; 400 si no viene ni una sola medida.
     */
    @Transactional
    public SignosVitalesResponseDto crear(SignosVitalesRequestDto request) {

        Enfermera enfermera = enfermeraAutenticado.exigir();

        Paciente paciente = pacienteRepository.findById(request.pacienteId())
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontro el paciente con id: " + request.pacienteId(), "404"));

        // Una toma parcial es normal; una toma VACIA no es una toma. Sin esta
        // guarda, un POST con solo el pacienteId dejaria en el expediente una
        // fila que dice que alguien tomo constantes y no dice ninguna, que es
        // exactamente el tipo de registro que ensucia una auditoria.
        if (sinNingunaMedida(request)) {
            throw new GeneralException(
                    "Hay que registrar al menos una medida: una toma sin ninguna constante no es una toma",
                    "400");
        }

        Consulta consulta = null;
        if (request.consultaId() != null) {
            consulta = consultaRepository.findById(request.consultaId())
                    .orElseThrow(() -> new NoResourceFoundException(
                            "No se encontro la consulta con id: " + request.consultaId(), "404"));
        }

        // tomadoEn lo pone el servidor cuando no viene. Se permite mandarlo
        // porque enfermeria a veces captura minutos despues de haber tomado
        // la constante en papel, y la hora que importa clinicamente es la de
        // la toma, no la del tecleo.
        LocalDateTime tomadoEn = request.tomadoEn() == null ? LocalDateTime.now() : request.tomadoEn();

        SignosVitales toma = SignosVitales.builder()
                .paciente(paciente)
                .enfermera(enfermera)
                .consulta(consulta)
                .tomadoEn(tomadoEn)
                .pesoKg(request.pesoKg())
                .estaturaCm(request.estaturaCm())
                .temperaturaC(request.temperaturaC())
                .presionSistolica(request.presionSistolica())
                .presionDiastolica(request.presionDiastolica())
                .pulsoLpm(request.pulsoLpm())
                .frecuenciaRespRpm(request.frecuenciaRespRpm())
                .saturacionPct(request.saturacionPct())
                .observaciones(request.observaciones())
                .build();

        return toDto(signosVitalesRepository.save(toma));
    }

    /**
     * Tomas de constantes, de lo mas reciente a lo mas antiguo.
     *
     * Con `pacienteId` es el historico de ese paciente, y un id inexistente da
     * 404 -a diferencia del listado de recetas, donde pacienteId es uno entre
     * varios filtros combinables y una pagina vacia es la respuesta correcta;
     * aqui el paciente ES el recurso que se pide.
     *
     * Sin `pacienteId` son todas: la pantalla de enfermeria no es el
     * expediente de alguien sino la lista de trabajo del turno, y ahi lo que
     * se quiere ver es lo ultimo que se tomo, de quien sea.
     */
    @Transactional(readOnly = true)
    public PaginaDto<SignosVitalesResponseDto> listar(
            Long pacienteId, Integer pagina, Integer tamano) {

        if (pacienteId != null) {
            exigirPacienteExistente(pacienteId);
        }

        int paginaSolicitada = pagina == null ? 0 : pagina;
        int tamanoSolicitado = tamano == null ? TAMANO_PAGINA_POR_DEFECTO : tamano;

        if (paginaSolicitada < 0) {
            throw new GeneralException("La pagina no puede ser negativa", "400");
        }
        if (tamanoSolicitado < 1 || tamanoSolicitado > TAMANO_PAGINA_MAXIMO) {
            throw new GeneralException(
                    "El tamano de pagina debe estar entre 1 y " + TAMANO_PAGINA_MAXIMO, "400");
        }

        Pageable pageable = PageRequest.of(paginaSolicitada, tamanoSolicitado);
        Page<SignosVitales> resultado = pacienteId == null
                ? signosVitalesRepository.findAllByOrderByTomadoEnDesc(pageable)
                : signosVitalesRepository.findByPaciente_PersonaIdOrderByTomadoEnDesc(pacienteId, pageable);

        return PaginaDto.de(resultado.map(this::toDto));
    }

    /**
     * La ultima toma del paciente, que es lo que el medico mira antes de
     * diagnosticar.
     *
     * Devuelve null -no 404- cuando el paciente existe pero nadie le ha
     * tomado constantes todavia. La distincion importa para quien pinta la
     * ficha: "este paciente no existe" es un error, "aun no le han tomado
     * nada" es el estado normal de un paciente recien registrado, y la
     * pantalla ya sabe mostrar ese hueco.
     */
    @Transactional(readOnly = true)
    public SignosVitalesResponseDto ultimaDelPaciente(Long pacienteId) {
        exigirPacienteExistente(pacienteId);
        return signosVitalesRepository
                .findFirstByPaciente_PersonaIdOrderByTomadoEnDesc(pacienteId)
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public SignosVitalesResponseDto obtener(Long signosVitalesId) {
        return toDto(signosVitalesRepository.findById(signosVitalesId)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontro la toma de signos vitales con id: " + signosVitalesId, "404")));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    private void exigirPacienteExistente(Long pacienteId) {
        if (!pacienteRepository.existsById(pacienteId)) {
            throw new NoResourceFoundException(
                    "No se encontro el paciente con id: " + pacienteId, "404");
        }
    }

    private static boolean sinNingunaMedida(SignosVitalesRequestDto r) {
        return r.pesoKg() == null
                && r.estaturaCm() == null
                && r.temperaturaC() == null
                && r.presionSistolica() == null
                && r.presionDiastolica() == null
                && r.pulsoLpm() == null
                && r.frecuenciaRespRpm() == null
                && r.saturacionPct() == null;
    }

    private SignosVitalesResponseDto toDto(SignosVitales toma) {

        Persona personaPaciente = toma.getPaciente().getPersona();
        Persona personaEnfermera = toma.getEnfermera().getPersona();

        return new SignosVitalesResponseDto(
                toma.getSignosVitalesId(),
                toma.getTomadoEn(),
                new PacienteResumenDto(
                        toma.getPaciente().getPersonaId(),
                        toma.getPaciente().getExpediente(),
                        personaPaciente.getNombres(),
                        personaPaciente.getApellidos()),
                new EnfermeraTomaDto(
                        toma.getEnfermera().getPersonaId(),
                        personaEnfermera.getNombres(),
                        personaEnfermera.getApellidos()),
                toma.getConsulta() == null ? null : toma.getConsulta().getConsultaId(),
                toma.getPesoKg(),
                toma.getEstaturaCm(),
                toma.getTemperaturaC(),
                toma.getPresionSistolica(),
                toma.getPresionDiastolica(),
                toma.getPulsoLpm(),
                toma.getFrecuenciaRespRpm(),
                toma.getSaturacionPct(),
                toma.getObservaciones());
    }
}
