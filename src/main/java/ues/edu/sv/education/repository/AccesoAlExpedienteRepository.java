package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ues.edu.sv.education.model.entity.AccesoAlExpediente;

import java.util.List;

public interface AccesoAlExpedienteRepository extends JpaRepository<AccesoAlExpediente, Long> {

    /** Lo más reciente primero; el id desempata dos accesos del mismo instante. */
    List<AccesoAlExpediente> findByPacienteIdOrderByAccedidoEnDescAccesoIdDesc(Long pacienteId);
}
