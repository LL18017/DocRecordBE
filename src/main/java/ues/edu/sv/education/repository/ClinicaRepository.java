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

    /**
     * Si ya hay una clinica con ese nombre en ese municipio (HU-26 criterio 4).
     *
     * Compara sin tildes y sin distinguir mayusculas, con la misma funcion que
     * usa la busqueda de pacientes (V13). "Clinica San Jose" y "Clínica San
     * José" son el mismo sitio escrito por dos personas distintas, y una
     * comparacion literal las dejaria convivir -- que es justo el duplicado que
     * el criterio quiere evitar.
     *
     * `excluirId` deja fuera a la propia clinica al editarla: sin eso, guardar
     * una clinica sin cambiarle el nombre chocaria consigo misma.
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM clinicas c
                WHERE LOWER(sin_tildes(c.name))      = LOWER(sin_tildes(:nombre))
                  AND LOWER(sin_tildes(c.municipio)) = LOWER(sin_tildes(:municipio))
                  AND (:excluirId IS NULL OR c.clinica_id <> :excluirId)
            )
            """, nativeQuery = true)
    boolean existeEnElMunicipio(@Param("nombre") String nombre,
                                @Param("municipio") String municipio,
                                @Param("excluirId") Integer excluirId);
}
