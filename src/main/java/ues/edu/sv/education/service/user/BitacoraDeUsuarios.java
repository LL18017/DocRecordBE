package ues.edu.sv.education.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ues.edu.sv.education.model.dto.User.BitacoraDeUsuarioResponseDto;
import ues.edu.sv.education.model.entity.BitacoraDeUsuario;
import ues.edu.sv.education.repository.BitacoraDeUsuarioRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Escribe y lee la bitacora de administracion de cuentas (HU-05 criterio 5).
 *
 * Es un componente aparte y no metodos de UserService para que UserService
 * solo diga QUE paso ("asigne este rol") y no COMO se guarda. Se invoca desde
 * dentro de las transacciones de UserService, asi que la fila de bitacora y el
 * cambio que describe se confirman o se deshacen juntos: no puede quedar un
 * cambio sin huella ni una huella de un cambio que no ocurrio.
 */
@Component
@RequiredArgsConstructor
public class BitacoraDeUsuarios {

    private final BitacoraDeUsuarioRepository repositorio;

    public void registrar(Integer usuarioAfectadoId, String accion, String detalle, String realizadoPor) {
        repositorio.save(BitacoraDeUsuario.builder()
                .usuarioAfectadoId(usuarioAfectadoId)
                .accion(accion)
                .detalle(detalle)
                .realizadoPor(realizadoPor)
                .realizadoEn(LocalDateTime.now())
                .build());
    }

    public List<BitacoraDeUsuarioResponseDto> deUsuario(Integer usuarioAfectadoId) {
        return repositorio.findByUsuarioAfectadoIdOrderByRealizadoEnDescIdDesc(usuarioAfectadoId).stream()
                .map(b -> new BitacoraDeUsuarioResponseDto(
                        b.getAccion(), b.getDetalle(), b.getRealizadoPor(), b.getRealizadoEn()))
                .toList();
    }
}
