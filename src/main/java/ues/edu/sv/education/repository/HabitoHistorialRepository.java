package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ues.edu.sv.education.model.entity.HabitoHistorial;

import java.util.List;

@Repository
public interface HabitoHistorialRepository extends JpaRepository<HabitoHistorial, Long> {

    /** Las versiones anteriores de un habito, la mas reciente primero. */
    List<HabitoHistorial> findByHabito_IdOrderByHistorialIdDesc(Long habitoId);
}
