package ues.edu.sv.education.service.medicamento;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.medicamento.MedicamentoCatalogoRequestDto;
import ues.edu.sv.education.model.dto.medicamento.MedicamentoCatalogoResponseDto;
import ues.edu.sv.education.model.entity.Medicamento;
import ues.edu.sv.education.repository.MedicamentoRepository;

import java.util.List;

/**
 * Catalogo de medicamentos (HU-23).
 *
 * Existe para que la receta se escriba desde una lista controlada. Por eso
 * sus dos reglas son de integridad del catalogo y no de presentacion: que no
 * haya dos filas del mismo producto (criterio 2) y que nada se borre, solo se
 * desactive (criterio 4).
 */
@Service
@RequiredArgsConstructor
public class MedicamentoService {

    private final MedicamentoRepository medicamentoRepository;

    /**
     * El catalogo, filtrado por texto.
     *
     * Por defecto solo los activos: es la lista que alimenta el autocompletado
     * de la receta, y ofrecer ahi un medicamento desactivado seria ofrecer
     * algo que el backend va a rechazar al emitir. Los inactivos solo los pide
     * la pantalla de administracion, para poder reactivarlos; quien puede
     * pedirlos lo decide el controller.
     */
    @Transactional(readOnly = true)
    public List<MedicamentoCatalogoResponseDto> listar(String buscar, boolean incluirInactivos) {
        String filtro = buscar == null ? "" : buscar.trim();
        return medicamentoRepository.buscar(filtro, incluirInactivos).stream()
                .map(MedicamentoService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public MedicamentoCatalogoResponseDto obtener(Long medicamentoId) {
        return toDto(buscarOFallar(medicamentoId));
    }

    @Transactional
    public MedicamentoCatalogoResponseDto crear(MedicamentoCatalogoRequestDto request) {
        rechazarDuplicado(request, -1L);
        Medicamento medicamento = Medicamento.builder().activo(true).build();
        copiar(request, medicamento);
        return toDto(medicamentoRepository.save(medicamento));
    }

    /**
     * Edita los datos de un medicamento.
     *
     * Las recetas ya emitidas no cambian: guardan la foto del nombre en su
     * propio renglon (ver V22). Corregir aqui una concentracion mal escrita
     * corrige lo que se recete de ahora en adelante, no lo que ya se entrego.
     */
    @Transactional
    public MedicamentoCatalogoResponseDto actualizar(Long medicamentoId, MedicamentoCatalogoRequestDto request) {
        Medicamento medicamento = buscarOFallar(medicamentoId);
        // El duplicado se busca ANTES de tocar la entidad: con la entidad ya
        // modificada, la consulta nativa obliga a Hibernate a volcarla primero,
        // el indice unico la rechaza y el 409 sale generico, sin decir cual
        // es el medicamento con el que choca.
        rechazarDuplicado(request, medicamento.getMedicamentoId());
        copiar(request, medicamento);
        return toDto(medicamentoRepository.save(medicamento));
    }

    /**
     * Activa o desactiva un medicamento. Nunca lo borra.
     *
     * Desactivar lo saca de las recetas NUEVAS (criterio 4); las emitidas lo
     * siguen mostrando porque lo referencian y guardan su nombre.
     */
    @Transactional
    public MedicamentoCatalogoResponseDto cambiarEstado(Long medicamentoId, boolean activo) {
        Medicamento medicamento = buscarOFallar(medicamentoId);
        medicamento.setActivo(activo);
        return toDto(medicamentoRepository.save(medicamento));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    private Medicamento buscarOFallar(Long medicamentoId) {
        return medicamentoRepository.findById(medicamentoId)
                .orElseThrow(() -> new NoResourceFoundException("Medicamento no encontrado", "404"));
    }

    /**
     * Criterio 2: el mismo nombre comercial, presentacion y concentracion que
     * otro producto se rechaza, y el 409 dice CUAL es el que ya existe.
     *
     * Se comprueba aqui ademas del indice unico de V22 porque la violacion del
     * indice llega como un DataIntegrityViolationException generico ("los
     * datos entran en conflicto con informacion existente"), que no nombra al
     * medicamento. El indice queda como red para dos altas simultaneas.
     *
     * Si el que ya existe esta desactivado se dice tambien: lo que el
     * administrador quiere en ese caso es reactivarlo, no crear otro.
     */
    private void rechazarDuplicado(MedicamentoCatalogoRequestDto request, long excluirId) {
        medicamentoRepository.buscarDuplicado(
                        request.nombreComercial(),
                        request.presentacion(),
                        request.concentracion(),
                        excluirId)
                .ifPresent(existente -> {
                    throw new GeneralException(
                            "Ya existe un medicamento con el mismo nombre comercial, presentacion y "
                                    + "concentracion: " + existente.descripcion()
                                    + " (codigo " + existente.getMedicamentoId() + ")"
                                    + (existente.isActivo() ? "" : ". Esta desactivado; reactivalo en vez de registrarlo otra vez")
                                    + ".",
                            "409");
                });
    }

    private static void copiar(MedicamentoCatalogoRequestDto request, Medicamento destino) {
        destino.setNombreGenerico(request.nombreGenerico().trim());
        destino.setNombreComercial(request.nombreComercial().trim());
        destino.setPrincipioActivo(request.principioActivo().trim());
        destino.setPresentacion(request.presentacion().trim());
        destino.setConcentracion(request.concentracion().trim());
    }

    public static MedicamentoCatalogoResponseDto toDto(Medicamento m) {
        return new MedicamentoCatalogoResponseDto(
                m.getMedicamentoId(),
                m.getNombreGenerico(),
                m.getNombreComercial(),
                m.getPrincipioActivo(),
                m.getPresentacion(),
                m.getConcentracion(),
                m.isActivo(),
                m.descripcion());
    }
}
