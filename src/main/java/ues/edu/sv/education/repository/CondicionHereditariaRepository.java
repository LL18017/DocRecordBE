package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ues.edu.sv.education.model.entity.CondicionHereditaria;

import java.util.Set;

public interface CondicionHereditariaRepository
        extends JpaRepository<CondicionHereditaria, Integer> {

    @Query("SELECT c FROM CondicionHereditaria c WHERE c.user.UserID = :userID")
    Set<CondicionHereditaria> findByUserID(
            @Param("userID") Integer userID
    );
}