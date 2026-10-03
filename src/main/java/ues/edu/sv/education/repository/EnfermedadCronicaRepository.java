package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ues.edu.sv.education.model.entity.EnfermedadCronica;

import java.util.Set;

public interface EnfermedadCronicaRepository
        extends JpaRepository<EnfermedadCronica, Integer> {

    @Query("SELECT e FROM EnfermedadCronica e WHERE e.user.UserID = :userID")
    Set<EnfermedadCronica> findByUserID(@Param("userID") Integer userID);

}