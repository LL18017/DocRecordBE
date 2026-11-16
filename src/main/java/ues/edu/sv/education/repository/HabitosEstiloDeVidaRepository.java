package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ues.edu.sv.education.model.entity.HabitosEstiloDeVida;

import java.util.List;

@Repository
public interface HabitosEstiloDeVidaRepository
        extends JpaRepository<HabitosEstiloDeVida, Long> {



    List<HabitosEstiloDeVida> findByPaciente_PersonaId(Long personaId);

    // Portal del paciente (DEF-07): solo los ACTIVOS.
    List<HabitosEstiloDeVida> findByPaciente_PersonaIdAndEstado(Long personaId, String estado);

    List<HabitosEstiloDeVida> findByTipoHabito_Id(Integer tipoHabitoId);
}