package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ues.edu.sv.education.model.entity.Persona;

public interface PersonaRepository extends JpaRepository<Persona, Long> {
}
