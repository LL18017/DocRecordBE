package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ues.edu.sv.education.entity.Carrera;
import ues.edu.sv.education.entity.Facultad;

public interface CarreraRepository extends JpaRepository<Carrera,Integer> {

}
