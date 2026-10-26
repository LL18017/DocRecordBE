package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Las citas AGENDADAS a futuro de una sede, las mas proximas primero
     * (HU-27 criterio 3). Con paciente y persona cargados porque el rechazo de
     * la baja las nombra. Las canceladas y las atendidas no estorban: ya no
     * van a ocurrir ahi.
     */
    @Query("""
    SELECT c
    FROM Cita c
      JOIN FETCH c.paciente p
        JOIN FETCH p.persona
    WHERE c.clinica.clinicaId = :clinicaId
      AND c.estado = 'AGENDADA'
      AND c.fechaHora > :desde
    ORDER BY c.fechaHora ASC
""")
    List<Cita> futurasAgendadasDeClinica(
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

    // HU-33: las citas AGENDADAS de la ventana que todavia no tienen
    // recordatorio. A una cita cancelada o atendida no se le recuerda nada
    // (criterio 3), y la marca recordatorio_enviado_en garantiza "una sola vez"
    // (criterio 1). La clinica va con LEFT JOIN: las citas anteriores a V24 no
    // la guardaron.
    @Query("""
    SELECT c
    FROM Cita c
      JOIN FETCH c.paciente p
        JOIN FETCH p.persona
        JOIN FETCH c.medico m
        JOIN FETCH m.persona
      LEFT JOIN FETCH c.clinica
    WHERE c.fechaHora BETWEEN :desde AND :hasta
      AND c.estado = 'AGENDADA'
      AND c.recordatorioEnviadoEn IS NULL
    ORDER BY c.fechaHora ASC
""")
    List<Cita> pendientesDeRecordatorio(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta
    );

    /**
     * Reclama el recordatorio de una cita: la marca solo si todavia no estaba
     * marcada. Devuelve 1 si la reclamo quien llama y 0 si otro (u otra
     * pasada) ya lo hizo, de modo que dos ejecuciones simultaneas nunca
     * mandan el mismo aviso.
     */
    @Modifying
    @Query("""
    UPDATE Cita c
       SET c.recordatorioEnviadoEn = :ahora
     WHERE c.citaId = :citaId
       AND c.recordatorioEnviadoEn IS NULL
       AND c.estado = 'AGENDADA'
""")
    int reclamarRecordatorio(
            @Param("citaId") Integer citaId,
            @Param("ahora") LocalDateTime ahora
    );

    /** Deshace la marca cuando el correo no salio, para reintentar en la pasada siguiente. */
    @Modifying
    @Query("UPDATE Cita c SET c.recordatorioEnviadoEn = NULL WHERE c.citaId = :citaId")
    int liberarRecordatorio(@Param("citaId") Integer citaId);

    @Query("""
        SELECT c
        FROM Cita c
        JOIN FETCH c.paciente p
        JOIN FETCH p.persona
        JOIN FETCH c.medico m
        JOIN FETCH m.persona
        LEFT JOIN FETCH c.clinica
        WHERE c.citaId = :citaId
    """)
    Optional<Cita> buscarCitaConPersonas(
            @Param("citaId") Integer citaId
    );
}
