package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ues.edu.sv.education.model.entity.Cita;
import ues.edu.sv.education.model.entity.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CitaRepository extends JpaRepository<Cita, Integer> {

    @Query("""
    SELECT c
    FROM Cita c
    INNER JOIN c.paciente p
    WHERE p.UserID = :pacienteId
    ORDER BY c.fechaHora ASC
""")
    List<Cita> findByPaciente_UserIDOrderByFechaHoraAsc(
            @Param("pacienteId") Integer pacienteId
    );

    @Query("""
    SELECT c
    FROM Cita c
    INNER JOIN c.medico m
    WHERE m.UserID = :medicoId
    ORDER BY c.fechaHora ASC
""")
    List<Cita> findByMedico_UserIDOrderByFechaHoraAsc(
            @Param("medicoId") Integer medicoId
    );

    List<Cita> findByEstado(String estado);


    @Query("""
    SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
    FROM Cita c
    INNER JOIN c.medico m
    WHERE m.UserID = :medicoId
      AND c.fechaHora = :fechaHora
""")
    boolean existsByMedico_UserIDAndFechaHora(
            @Param("medicoId") Integer medicoId,
            @Param("fechaHora") LocalDateTime fechaHora
    );

    @Query("""
    SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
    FROM Cita c
    INNER JOIN c.medico m
    WHERE m.UserID = :pacienteId
      AND c.fechaHora = :fechaHora
""")
    boolean existsByPaciente_UserIDAndFechaHora(
            Integer pacienteId,
            LocalDateTime fechaHora
    );


    @Query("""
    SELECT c
    FROM Cita c
      JOIN FETCH c.paciente p
        JOIN FETCH p.persona
        JOIN FETCH c.medico m
        JOIN FETCH m.persona
    WHERE  c.fechaHora between :fechaInicio and :fechaFinal
""")
    List<Cita> findByFechaHoraBetween(
            @Param("fechaInicio") LocalDateTime fechaHora,
            @Param("fechaFinal") LocalDateTime fechaFinal
    );

    @Query("""
        SELECT c
        FROM Cita c
        JOIN FETCH c.paciente p
        JOIN FETCH p.persona
        JOIN FETCH c.medico m
        JOIN FETCH m.persona
        WHERE c.citaId = :citaId
    """)
    Optional<Cita> buscarCitaConPersonas(
            @Param("citaId") Integer citaId
    );
}