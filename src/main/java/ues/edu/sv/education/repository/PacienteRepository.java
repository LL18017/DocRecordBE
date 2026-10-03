package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ues.edu.sv.education.model.entity.Paciente;

import java.util.List;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    /**
     * Los pacientes que coinciden con el texto buscado, en orden alfabetico.
     *
     * ── Por que es una consulta NATIVA ────────────────────────────────────
     * HU-07 criterio 3 pide encontrar al paciente "con distinta capitalizacion
     * o SIN TILDES". LOWER() resuelve lo primero; lo segundo necesita
     * `sin_tildes()`, la funcion que crea V13 sobre la extension unaccent.
     * JPQL solo conoce las funciones que Hibernate tiene registradas, y esta
     * es propia de esta base, asi que la consulta tiene que bajar a SQL.
     *
     * No se normaliza en Java antes de mandar el texto porque la comparacion
     * ocurre dentro del WHERE: quitarle las tildes a lo que teclea el usuario
     * solo arregla un lado, y lo que esta guardado sigue teniendolas.
     *
     * ── Por que devuelve ids y no filas ───────────────────────────────────
     * Al bajar a SQL se pierde el JOIN FETCH, y sin el cada `getPersona()`
     * dispara su propia consulta: con N pacientes, N+1 viajes a la base. Asi
     * que esta consulta solo decide QUIENES coinciden, y `conPersona` los trae
     * despues en un unico viaje con su persona ya cargada. Son dos consultas
     * fijas, no N+1.
     *
     * El DUI se compara sin sin_tildes a proposito: son digitos y un guion, no
     * tiene acentos que quitar, y aplicarle la funcion solo impediria usar su
     * indice.
     */
    /*
     * ── Por que el estado se filtra aqui y no en Java ─────────────────────
     * Porque la alternativa es traer a los inactivos para descartarlos
     * despues, y eso convierte el filtro en una mentira en cuanto el listado
     * se pagine: la primera pagina vendria con huecos. El WHERE es el unico
     * sitio donde "no aparece" significa de verdad no aparece.
     *
     * El parametro va con CAST explicito por la misma razon que el filtro de
     * texto nunca viaja nulo: un parametro suelto en el WHERE deja a Postgres
     * sin tipo que inferir y el driver lo manda como bytea.
     */
    @Query(value = """
            SELECT pa.persona_id
            FROM pacientes pa
            JOIN persona per ON per.persona_id = pa.persona_id
            WHERE (CAST(:incluirInactivos AS boolean) = true OR pa.estado = 'ACTIVO')
              AND (LOWER(sin_tildes(per.apellidos)) LIKE LOWER(sin_tildes(CONCAT('%', :buscar, '%')))
                OR LOWER(sin_tildes(per.nombres))   LIKE LOWER(sin_tildes(CONCAT('%', :buscar, '%')))
                OR LOWER(per.dui)                   LIKE LOWER(CONCAT('%', :buscar, '%')))
            ORDER BY per.apellidos, per.nombres
            """, nativeQuery = true)
    List<Long> idsQueCoinciden(@Param("buscar") String buscar,
                               @Param("incluirInactivos") boolean incluirInactivos);

    /**
     * Trae esos pacientes con su persona ya cargada, en el mismo orden.
     *
     * El ORDER BY se repite aqui porque un IN no conserva el orden de la lista
     * que recibe: sin el, la tabla saldria ordenada por lo que decida Postgres.
     */
    @Query("""
            SELECT p
            FROM Paciente p
            JOIN FETCH p.persona per
            WHERE p.personaId IN :ids
            ORDER BY per.apellidos, per.nombres
            """)
    List<Paciente> conPersona(@Param("ids") List<Long> ids);

    // El correlativo lo entrega una secuencia de PostgreSQL, no MAX+1: dos
    // altas simultaneas leerian el mismo maximo y chocarian contra el UNIQUE.
    @Query(value = "SELECT nextval('expediente_seq')", nativeQuery = true)
    Long siguienteCorrelativoDeExpediente();
}
