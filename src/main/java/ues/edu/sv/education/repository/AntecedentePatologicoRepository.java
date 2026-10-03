package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ues.edu.sv.education.model.entity.AntecedentePatologico;

import java.util.List;

@Repository
public interface AntecedentePatologicoRepository extends JpaRepository<AntecedentePatologico, Long> {

    /**
     * Los antecedentes de un paciente, del mas reciente al mas antiguo
     * (criterio 2 de HU-12). A igual fecha, el ultimo registrado primero.
     *
     * Trae al medico y su persona en la misma consulta: la respuesta lleva su
     * nombre, y sin el grafo cada fila dispararia dos consultas mas.
     */
    @EntityGraph(attributePaths = {"medico", "medico.persona"})
    List<AntecedentePatologico> findByPaciente_PersonaIdOrderByFechaDescAntecedenteIdDesc(Long pacienteId);
}
