package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ues.edu.sv.education.model.entity.Consulta;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {

    /**
     * Las consultas, de la mas reciente a la mas antigua, con filtros
     * opcionales y combinables: paciente, medico y rango de fechas (HU-20,
     * criterio 2). Un filtro que llega null no restringe nada.
     *
     * El desempate por consultaId no es un adorno: dos consultas del mismo dia
     * pueden compartir el instante (la fecha la pone el servidor y varias
     * altas seguidas caen en el mismo milisegundo en las pruebas). Sin el, el
     * orden entre ellas seria el que quiera PostgreSQL, y "el ultimo control"
     * dejaria de ser un dato confiable.
     *
     * Los JOIN FETCH traen persona, especialidad y clinica en la misma
     * consulta: sin ellos, armar el DTO de cada fila dispara una consulta por
     * relacion (N+1) y el historial de un paciente con 30 visitas serian mas
     * de 100 viajes a la base.
     *
     * Cada condicion usa coalesce(:x, columna) y no "(:x IS NULL OR ...)" por
     * la misma razon que PrescripcionRepository.buscar: con un parametro null
     * PostgreSQL no logra inferir su tipo y la consulta revienta con 500.
     * Para "hasta" (exclusivo) el valor por defecto es fecha + 1 dia, siempre
     * mayor que la propia fecha.
     */
    @Query("""
    SELECT c
    FROM Consulta c
    JOIN FETCH c.paciente pac
    JOIN FETCH pac.persona
    JOIN FETCH c.medico med
    JOIN FETCH med.persona
    JOIN FETCH med.especialidad
    LEFT JOIN FETCH c.clinica
    WHERE pac.personaId = coalesce(:pacienteId, pac.personaId)
      AND med.personaId = coalesce(:medicoId, med.personaId)
      AND c.fecha >= coalesce(:desde, c.fecha)
      AND c.fecha < coalesce(:hasta, c.fecha + 1 day)
    ORDER BY c.fecha DESC, c.consultaId DESC
""")
    List<Consulta> buscar(
            @Param("pacienteId") Long pacienteId,
            @Param("medicoId") Long medicoId,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    @Query("""
    SELECT c
    FROM Consulta c
    JOIN FETCH c.paciente pac
    JOIN FETCH pac.persona
    JOIN FETCH c.medico med
    JOIN FETCH med.persona
    JOIN FETCH med.especialidad
    LEFT JOIN FETCH c.clinica
    WHERE c.consultaId = :consultaId
""")
    Optional<Consulta> buscarConDetalle(@Param("consultaId") Long consultaId);
}
