package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ues.edu.sv.education.model.entity.Prescripcion;

import java.util.List;
import java.util.Optional;

public interface PrescripcionRepository extends JpaRepository<Prescripcion, Long> {

    /**
     * Las recetas de una consulta.
     *
     * Los renglones de medicamentos NO se traen con JOIN FETCH a proposito:
     * al mezclar una coleccion con el ORDER BY, la base devuelve una fila por
     * medicamento y el orden de las recetas deja de ser el que se pidio. Se
     * cargan despues, dentro de la misma transaccion, al armar el DTO.
     */
    @Query("""
    SELECT p
    FROM Prescripcion p
    JOIN FETCH p.medico med
    JOIN FETCH med.persona
    WHERE p.consulta.consultaId = :consultaId
    ORDER BY p.fecha DESC, p.prescripcionId DESC
""")
    List<Prescripcion> buscarPorConsulta(@Param("consultaId") Long consultaId);

    /**
     * Todas las recetas de un paciente, sin importar en que consulta se
     * emitieron: es la pregunta "que le han recetado a esta persona", que en
     * papel obliga a hojear el expediente entero.
     */
    @Query("""
    SELECT p
    FROM Prescripcion p
    JOIN FETCH p.medico med
    JOIN FETCH med.persona
    JOIN p.consulta c
    WHERE c.paciente.personaId = :pacienteId
    ORDER BY p.fecha DESC, p.prescripcionId DESC
""")
    List<Prescripcion> buscarPorPaciente(@Param("pacienteId") Long pacienteId);

    @Query("""
    SELECT p
    FROM Prescripcion p
    JOIN FETCH p.medico med
    JOIN FETCH med.persona
    WHERE p.prescripcionId = :prescripcionId
""")
    Optional<Prescripcion> buscarConDetalle(@Param("prescripcionId") Long prescripcionId);
}
