package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ues.edu.sv.education.model.entity.Especialidad;

import java.util.List;

public interface EspecialidadRepository extends JpaRepository<Especialidad, Long> {

    List<Especialidad> findByActivaTrueOrderByNombre();
}
