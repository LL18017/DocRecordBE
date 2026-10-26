package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ues.edu.sv.education.model.entity.HorarioDeMedico;

import java.util.List;

public interface HorarioDeMedicoRepository extends JpaRepository<HorarioDeMedico, Integer> {

    /** Las franjas de un medico un dia de la semana, en TODAS sus sedes: para detectar traslapes. */
    @Query("""
            SELECT h FROM HorarioDeMedico h JOIN FETCH h.clinica
            WHERE h.medico.UserID = :medicoId AND h.diaSemana = :dia
            """)
    List<HorarioDeMedico> delMedicoElDia(@Param("medicoId") Integer medicoId, @Param("dia") Short dia);

    @Query("""
            SELECT h FROM HorarioDeMedico h
            WHERE h.clinica.clinicaId = :clinicaId
            ORDER BY h.diaSemana, h.horaInicio
            """)
    List<HorarioDeMedico> deLaClinica(@Param("clinicaId") Integer clinicaId);

    @Query("""
            SELECT h FROM HorarioDeMedico h
            WHERE h.clinica.clinicaId = :clinicaId AND h.medico.UserID = :medicoId
            ORDER BY h.diaSemana, h.horaInicio
            """)
    List<HorarioDeMedico> delMedicoEnLaClinica(@Param("clinicaId") Integer clinicaId,
                                               @Param("medicoId") Integer medicoId);
}
