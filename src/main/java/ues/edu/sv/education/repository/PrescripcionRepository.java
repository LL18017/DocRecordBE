package ues.edu.sv.education.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ues.edu.sv.education.model.entity.Prescripcion;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PrescripcionRepository extends JpaRepository<Prescripcion, Long> {

    /**
     * El historico de recetas, filtrado y paginado.
     *
     * Todos los filtros son opcionales y COMBINABLES: cada condicion se anula
     * a si misma cuando el parametro llega null (ver el comentario de mas
     * abajo sobre por que es coalesce(:x, columna) y no "(:x IS NULL OR
     * ...)"), asi que pedir solo pacienteId, solo medicoId, los dos juntos,
     * un rango de fechas, o ninguno, cae siempre en esta misma consulta. Antes existian
     * buscarPorConsulta y buscarPorPaciente por separado porque el servicio
     * EXIGIA elegir un solo filtro; ahora que el filtro es libre, tenerlos
     * separados solo hubiera significado repetir esta misma logica dos o tres
     * veces.
     *
     * Los JOIN FETCH son todos hacia el lado *ToOne (medico, persona,
     * consulta, paciente): a diferencia de "medicamentos" (una coleccion, ver
     * el comentario que tenia este archivo antes de este cambio), estos no
     * multiplican filas, asi que es seguro combinarlos con Pageable sin que
     * la paginacion salga mal (una coleccion fetch-join con LIMIT/OFFSET
     * pagina en memoria, y eso si seria un problema).
     *
     * No lleva ORDER BY propio: el orden (mas reciente primero) lo pone el
     * Sort del Pageable que arma el servicio. Ponerlo aqui ADEMAS del Sort
     * generaria dos ORDER BY en el JPQL resultante.
     *
     * ── Por que cada condicion usa coalesce(...) y no "(:x IS NULL OR ...)" ──
     * Con el patron (:x IS NULL OR columna = :x) tal cual, PostgreSQL/JDBC no
     * logra inferir el tipo del parametro cuando el valor real es NULL: no
     * hay ninguna otra aparicion de :x en la consulta que le de contexto de
     * tipo, y el resultado es SQLGrammarException ("could not determine data
     * type of parameter $N"), que aqui se ve como 500 en vez de 200 en
     * CUALQUIER llamada a /prescripciones (incluso sin filtros, porque los 5
     * parametros siempre viajan, null o no).
     *
     * El intento obvio -castear el parametro explicitamente con cast(:x as
     * long)/cast(:x as timestamp)- SE PROBO y NO sirve aqui: la consulta
     * compila y el SQL generado se ve correcto ("cast(? as bigint)"), pero en
     * runtime Hibernate 7.4.1 le manda a Postgres ESE parametro con el tipo
     * JDBC equivocado (bytea) sin importar el nombre de tipo usado en el
     * cast(long, timestamp, java.lang.Long, java.time.LocalDateTime probados
     * todos), y Postgres responde "cannot cast type bytea to bigint/
     * timestamp" -un defecto de Hibernate en como resuelve el tipo de bind de
     * un parametro nombrado envuelto en cast(), no un problema de sintaxis.
     *
     * coalesce(:x, columna) evita el problema por otra via: en vez de
     * "anular" la condicion cuando el parametro es null, la hace TRIVIALMENTE
     * CIERTA comparando la columna contra si misma. Postgres SI logra inferir
     * el tipo de :x aqui porque coalesce fuerza que todos sus argumentos
     * compartan tipo, y el otro argumento (la columna) ya tiene uno concreto
     * -exactamente la pista de tipo que "IS NULL" no daba. Sin cast() de por
     * medio, el defecto de Hibernate no aplica.
     *
     * Para "hasta" (comparacion exclusiva, p.fecha < hasta) no basta con
     * comparar la columna contra si misma (fecha < fecha es SIEMPRE falso, lo
     * que descartaria todo cuando no hay filtro): el "valor por defecto" debe
     * quedar estrictamente por encima de fecha, asi que se usa fecha + 1 dia
     * (aritmetica de fechas soportada en HQL desde Hibernate 6), que es
     * siempre mayor que fecha misma.
     */
    @Query(
            value = """
            SELECT p
            FROM Prescripcion p
            JOIN FETCH p.medico med
            JOIN FETCH med.persona
            JOIN FETCH p.consulta c
            JOIN FETCH c.paciente pac
            JOIN FETCH pac.persona
            WHERE pac.personaId = coalesce(:pacienteId, pac.personaId)
              AND med.personaId = coalesce(:medicoId, med.personaId)
              AND c.consultaId = coalesce(:consultaId, c.consultaId)
              AND p.fecha >= coalesce(:desde, p.fecha)
              AND p.fecha < coalesce(:hasta, p.fecha + 1 day)
            """,
            countQuery = """
            SELECT count(p)
            FROM Prescripcion p
            JOIN p.medico med
            JOIN p.consulta c
            JOIN c.paciente pac
            WHERE pac.personaId = coalesce(:pacienteId, pac.personaId)
              AND med.personaId = coalesce(:medicoId, med.personaId)
              AND c.consultaId = coalesce(:consultaId, c.consultaId)
              AND p.fecha >= coalesce(:desde, p.fecha)
              AND p.fecha < coalesce(:hasta, p.fecha + 1 day)
            """
    )
    Page<Prescripcion> buscar(
            @Param("pacienteId") Long pacienteId,
            @Param("medicoId") Long medicoId,
            @Param("consultaId") Long consultaId,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            Pageable pageable);

    /**
     * Una receta con todo lo que su DTO necesita: la firma (medico+persona) Y
     * el paciente (consulta+paciente+persona). Antes solo traia la firma
     * porque el DTO no llevaba paciente; sin este JOIN FETCH, toDto()
     * dispararia dos consultas mas por receta al pedir
     * consulta.getPaciente().getPersona() (N+1 en /prescripciones, y una
     * lazy-load de mas incluso para una sola receta en GET /{id}).
     */
    @Query("""
    SELECT p
    FROM Prescripcion p
    JOIN FETCH p.medico med
    JOIN FETCH med.persona
    JOIN FETCH p.consulta c
    JOIN FETCH c.paciente pac
    JOIN FETCH pac.persona
    WHERE p.prescripcionId = :prescripcionId
""")
    Optional<Prescripcion> buscarConDetalle(@Param("prescripcionId") Long prescripcionId);
}
