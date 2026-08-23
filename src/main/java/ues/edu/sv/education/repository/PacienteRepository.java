package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ues.edu.sv.education.model.entity.Paciente;

import java.util.List;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    // Una sola caja de busqueda: coincide contra apellidos, nombres o DUI.
    // El parametro NUNCA debe llegar null (ver PacienteService.buscar): un
    // parametro null ligado en varios puntos de la misma consulta hace que
    // Postgres no pueda inferir su tipo y el driver lo manda como bytea,
    // reventando con "function lower(bytea) does not exist". Para "sin
    // filtro" se pasa "" -LIKE '%%' coincide con cualquier valor no nulo-
    // en vez de resolverlo con SQL condicional.
    @Query("""
    SELECT p
    FROM Paciente p
    JOIN FETCH p.persona per
    WHERE LOWER(per.apellidos) LIKE LOWER(CONCAT('%', :buscar, '%'))
       OR LOWER(per.nombres) LIKE LOWER(CONCAT('%', :buscar, '%'))
       OR LOWER(per.dui) LIKE LOWER(CONCAT('%', :buscar, '%'))
    ORDER BY per.apellidos, per.nombres
""")
    List<Paciente> buscar(@Param("buscar") String buscar);
}
