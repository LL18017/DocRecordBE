package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ues.edu.sv.education.model.entity.Paciente;

import java.util.List;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    // Una sola caja de busqueda: coincide contra apellidos, nombres o DUI.
    // Sin :buscar (null) devuelve todos.
    @Query("""
    SELECT p
    FROM Paciente p
    JOIN FETCH p.persona per
    WHERE :buscar IS NULL
       OR LOWER(per.apellidos) LIKE LOWER(CONCAT('%', :buscar, '%'))
       OR LOWER(per.nombres) LIKE LOWER(CONCAT('%', :buscar, '%'))
       OR LOWER(per.dui) LIKE LOWER(CONCAT('%', :buscar, '%'))
    ORDER BY per.apellidos, per.nombres
""")
    List<Paciente> buscar(@Param("buscar") String buscar);
}
