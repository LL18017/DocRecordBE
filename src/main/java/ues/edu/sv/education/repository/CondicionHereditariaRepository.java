package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ues.edu.sv.education.model.entity.CondicionHereditaria;

import java.util.List;

public interface CondicionHereditariaRepository
        extends JpaRepository<CondicionHereditaria, Integer> {

    List<CondicionHereditaria> findByPaciente_PersonaId(Long pacienteId);
}
