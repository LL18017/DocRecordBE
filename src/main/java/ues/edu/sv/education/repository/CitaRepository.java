package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ues.edu.sv.education.model.entity.Cita;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CitaRepository extends JpaRepository<Cita, Integer> {

    @Query("""
    SELECT c
    FROM Cita c
    WHERE c.paciente.personaId = :pacienteId
    ORDER BY c.fechaHora ASC
""")
    List<Cita> findByPacienteOrderByFechaHoraAsc(
            @Param("pacienteId") Long pacienteId
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

    /** La agenda de una sede en un rango, canceladas incluidas: siguen en el historial. */
    @Query("""
    SELECT c
    FROM Cita c
    WHERE c.clinica.clinicaId = :clinicaId
      AND c.fechaHora >= :desde
      AND c.fechaHora < :hasta
    ORDER BY c.fechaHora ASC
""")
    List<Cita> agendaDeClinica(
            @Param("clinicaId") Integer clinicaId,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta
    );

    @Query("""
    SELECT c
    FROM Cita c
    WHERE c.medico.UserID = :medicoId
      AND c.fechaHora >= :desde
      AND c.fechaHora < :hasta
    ORDER BY c.fechaHora ASC
""")
    List<Cita> agendaDeMedico(
            @Param("medicoId") Integer medicoId,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta
    );

    /** Las citas vivas a futuro de un medico en una sede (HU-29, criterio 4). */
    @Query("""
    SELECT c
    FROM Cita c
    WHERE c.medico.UserID = :medicoId
      AND c.clinica.clinicaId = :clinicaId
      AND c.estado = 'AGENDADA'
      AND c.fechaHora > :desde
    ORDER BY c.fechaHora ASC
""")
    List<Cita> futurasDelMedicoEnLaClinica(
            @Param("medicoId") Integer medicoId,
            @Param("clinicaId") Integer clinicaId,
            @Param("desde") LocalDateTime desde
    );

    List<Cita> findByEstado(String estado);

    // Las dos de disponibilidad ignoran las canceladas: cancelar libera la
    // franja (HU-32). `excluirCitaId` es la cita que se esta reprogramando,
    // que no choca consigo misma; al agendar se pasa -1.
    @Query("""
    SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
    FROM Cita c
    WHERE c.medico.UserID = :medicoId
      AND c.fechaHora = :fechaHora
      AND c.estado <> 'CANCELADA'
      AND c.citaId <> :excluirCitaId
""")
    boolean medicoOcupado(
            @Param("medicoId") Integer medicoId,
            @Param("fechaHora") LocalDateTime fechaHora,
            @Param("excluirCitaId") Integer excluirCitaId
    );

    @Query("""
    SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
    FROM Cita c
    WHERE c.paciente.personaId = :pacienteId
      AND c.fechaHora = :fechaHora
      AND c.estado <> 'CANCELADA'
      AND c.citaId <> :excluirCitaId
""")
    boolean pacienteOcupado(
            @Param("pacienteId") Long pacienteId,
            @Param("fechaHora") LocalDateTime fechaHora,
            @Param("excluirCitaId") Integer excluirCitaId
    );

    // Solo las AGENDADAS: a una cita cancelada o atendida no se le recuerda
    // nada (HU-33, criterio 3).
    @Query("""
    SELECT c
    FROM Cita c
      JOIN FETCH c.paciente p
        JOIN FETCH p.persona
        JOIN FETCH c.medico m
        JOIN FETCH m.persona
    WHERE  c.fechaHora between :fechaInicio and :fechaFinal
      AND c.estado = 'AGENDADA'
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
