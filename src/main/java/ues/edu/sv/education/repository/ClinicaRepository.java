package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ues.edu.sv.education.model.entity.Clinicas;

import java.util.List;

public interface ClinicaRepository extends JpaRepository<Clinicas,Integer> {
    @Query("""
    SELECT c
    FROM Clinicas c
    JOIN c.user u
    WHERE u.UserID = :idUser
""")
    List<Clinicas> findByUser(@Param("idUser") Integer idUser);
}
