package ues.edu.sv.education.service.portal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.model.dto.portal.AccesoAlExpedienteDto;
import ues.edu.sv.education.model.entity.AccesoAlExpediente;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.repository.AccesoAlExpedienteRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Escribe y lee la bitacora de accesos al expediente (HU-34 criterio 6).
 *
 * Mismo reparto que BitacoraDeUsuarios: el portal dice QUE paso ("el paciente
 * abrio su expediente") y esto decide COMO se guarda. Se invoca dentro de la
 * transaccion de quien arma el expediente, asi que la huella queda solo si el
 * expediente de verdad se entrego: un PDF que fallo al generarse no deja un
 * "lo descargo" que no ocurrio.
 */
@Component
@RequiredArgsConstructor
public class BitacoraDeAccesos {

    private final AccesoAlExpedienteRepository repositorio;

    public void registrar(Long pacienteId, User quien, String tipo) {
        repositorio.save(AccesoAlExpediente.builder()
                .pacienteId(pacienteId)
                .usuarioId(quien.getUserID())
                .correoDeSesion(quien.getEmail())
                .tipo(tipo)
                .accedidoEn(LocalDateTime.now())
                .build());
    }

    @Transactional(readOnly = true)
    public List<AccesoAlExpedienteDto> dePaciente(Long pacienteId) {
        return repositorio.findByPacienteIdOrderByAccedidoEnDescAccesoIdDesc(pacienteId).stream()
                .map(a -> new AccesoAlExpedienteDto(a.getTipo(), a.getCorreoDeSesion(), a.getAccedidoEn()))
                .toList();
    }
}
