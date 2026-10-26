package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ues.edu.sv.education.model.entity.BitacoraDeUsuario;

import java.util.List;

public interface BitacoraDeUsuarioRepository extends JpaRepository<BitacoraDeUsuario, Long> {

    /** Lo más reciente primero; el id desempata dos acciones del mismo instante. */
    List<BitacoraDeUsuario> findByUsuarioAfectadoIdOrderByRealizadoEnDescIdDesc(Integer usuarioAfectadoId);
}
