package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ues.edu.sv.education.model.entity.Alergia;

import java.util.List;
import java.util.Optional;

public interface AlergiaRepository extends JpaRepository<Alergia, Integer> {

    /**
     * Las alergias vigentes de un paciente. Trae a quien las registro en la
     * misma consulta: la respuesta lleva su nombre, y sin el grafo cada fila
     * dispararia una consulta mas.
     */
    @EntityGraph(attributePaths = {"registradaPor"})
    List<Alergia> findByPaciente_PersonaIdAndEliminadaEnIsNull(Long pacienteId);

    /**
     * La alergia vigente de esa sustancia para ese paciente, si la hay
     * (criterio 3). Compara como lo hace el indice unico de V20 -sin
     * mayusculas, sin tildes y sin espacios alrededor- para que "Penicilina"
     * y " PENICILÍNA " sean la misma, y para que lo que este metodo deja pasar
     * sea exactamente lo que la base acepta.
     */
    @Query(value = """
            SELECT * FROM alergias
            WHERE paciente_id = :pacienteId
              AND eliminada_en IS NULL
              AND lower(public.sin_tildes(trim(sustancia))) = lower(public.sin_tildes(trim(:sustancia)))
            """, nativeQuery = true)
    Optional<Alergia> buscarVigentePorSustancia(@Param("pacienteId") Long pacienteId,
                                                @Param("sustancia") String sustancia);
}
