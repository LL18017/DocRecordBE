package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ues.edu.sv.education.model.entity.Consulta;

import java.util.List;
import java.util.Optional;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {

    /**
     * El historial de un paciente, de la consulta mas reciente a la mas
     * antigua.
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
    WHERE pac.personaId = :pacienteId
    ORDER BY c.fecha DESC, c.consultaId DESC
""")
    List<Consulta> buscarPorPaciente(@Param("pacienteId") Long pacienteId);

    /** Todas las consultas, mas reciente primero. Ver buscarPorPaciente. */
    @Query("""
    SELECT c
    FROM Consulta c
    JOIN FETCH c.paciente pac
    JOIN FETCH pac.persona
    JOIN FETCH c.medico med
    JOIN FETCH med.persona
    JOIN FETCH med.especialidad
    LEFT JOIN FETCH c.clinica
    ORDER BY c.fecha DESC, c.consultaId DESC
""")
    List<Consulta> buscarTodas();

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
