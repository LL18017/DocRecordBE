package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ues.edu.sv.education.model.entity.Alergia;

import java.util.Set;

public interface AlergiaRepository extends JpaRepository<Alergia, Integer> {

    @Query("SELECT a FROM Alergia a WHERE a.user.UserID = :userID")
    Set<Alergia> findByUserID(@Param("userID") Integer userID);

}