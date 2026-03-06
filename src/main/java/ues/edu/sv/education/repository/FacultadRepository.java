package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ues.edu.sv.education.entity.Facultad;

import java.util.List;

public interface FacultadRepository extends JpaRepository<Facultad,Integer> {
}
