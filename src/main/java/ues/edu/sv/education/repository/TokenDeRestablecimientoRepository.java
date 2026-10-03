package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ues.edu.sv.education.model.entity.TokenDeRestablecimiento;
import ues.edu.sv.education.model.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * Los tokens de recuperacion de contrasena (HU-04).
 *
 * Aparte de VerificationTokenRepository a proposito: son dos credenciales
 * distintas y ninguna consulta de aqui puede alcanzar un token de confirmacion
 * de correo, ni al reves. Ver la cabecera de TokenDeRestablecimiento.
 */
public interface TokenDeRestablecimientoRepository extends JpaRepository<TokenDeRestablecimiento, Long> {

    Optional<TokenDeRestablecimiento> findByToken(String token);

    /**
     * Los enlaces que esta cuenta todavia podria canjear.
     *
     * Se usa al restablecer, para invalidar los demas: si alguien mas pidio
     * recuperar esa cuenta -- un intento de apropiacion, o el propio usuario
     * pulsando el boton tres veces -- esos correos siguen en una bandeja de
     * entrada con enlaces vivos. En cuanto uno se canjea, los otros dejan de
     * tener sentido y son riesgo puro.
     *
     * Devuelve tambien los ya vencidos, que no molestan: marcarlos usados es
     * inofensivo y la alternativa -filtrar por fecha en la consulta- compara
     * contra un `now` distinto del que usa la entidad.
     */
    List<TokenDeRestablecimiento> findByUsuarioAndUsadoFalse(User usuario);
}
