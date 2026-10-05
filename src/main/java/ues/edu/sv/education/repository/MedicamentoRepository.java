package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ues.edu.sv.education.model.entity.Medicamento;

import java.util.List;
import java.util.Optional;

public interface MedicamentoRepository extends JpaRepository<Medicamento, Long> {

    /**
     * El catalogo filtrado por texto, para el autocompletado de la receta y la
     * pantalla de administracion.
     *
     * El texto se busca en el nombre generico, el comercial y el principio
     * activo: el medico puede pensar en "Panadol", en "acetaminofen" o en
     * "paracetamol", y los tres son el mismo producto. Sin tildes ni
     * mayusculas por la misma razon que la busqueda de pacientes (V13): se
     * teclea deprisa, con el paciente enfrente.
     *
     * Baja a SQL porque sin_tildes es una funcion de esta base que JPQL no
     * conoce. El texto nunca viaja nulo (el servicio manda '' para "sin
     * filtro") y el booleano lleva CAST: con un parametro suelto Postgres no
     * logra inferir el tipo (ver el mismo comentario en PacienteRepository).
     */
    @Query(value = """
            SELECT m.*
            FROM medicamentos m
            WHERE (CAST(:incluirInactivos AS boolean) = true OR m.activo = true)
              AND (LOWER(sin_tildes(m.nombre_generico))  LIKE LOWER(sin_tildes(CONCAT('%', :buscar, '%')))
                OR LOWER(sin_tildes(m.nombre_comercial)) LIKE LOWER(sin_tildes(CONCAT('%', :buscar, '%')))
                OR LOWER(sin_tildes(m.principio_activo)) LIKE LOWER(sin_tildes(CONCAT('%', :buscar, '%'))))
            ORDER BY LOWER(sin_tildes(m.nombre_generico)), LOWER(sin_tildes(m.nombre_comercial)),
                     m.concentracion, m.medicamento_id
            """, nativeQuery = true)
    List<Medicamento> buscar(@Param("buscar") String buscar,
                             @Param("incluirInactivos") boolean incluirInactivos);

    /**
     * El producto que ya ocupa esa combinacion de nombre comercial,
     * presentacion y concentracion (HU-23 criterio 2), si existe.
     *
     * Devuelve la fila y no un boolean porque el 409 tiene que decir CUAL es:
     * "ya existe" a secas deja al administrador buscandolo a mano en la lista.
     *
     * Compara con clave_de_catalogo, la misma funcion del indice unico de V22,
     * para que esta consulta y la base esten de acuerdo en que es "el mismo".
     * excluirId deja fuera al propio medicamento al editarlo: guardarlo sin
     * cambiar esos tres campos no es chocar consigo mismo. Llega como -1 y no
     * como null cuando no hay que excluir a nadie, para no tener un parametro
     * nulo sin tipo en el WHERE.
     */
    @Query(value = """
            SELECT m.*
            FROM medicamentos m
            WHERE clave_de_catalogo(m.nombre_comercial) = clave_de_catalogo(:nombreComercial)
              AND clave_de_catalogo(m.presentacion)     = clave_de_catalogo(:presentacion)
              AND clave_de_catalogo(m.concentracion)    = clave_de_catalogo(:concentracion)
              AND m.medicamento_id <> :excluirId
            LIMIT 1
            """, nativeQuery = true)
    Optional<Medicamento> buscarDuplicado(@Param("nombreComercial") String nombreComercial,
                                          @Param("presentacion") String presentacion,
                                          @Param("concentracion") String concentracion,
                                          @Param("excluirId") long excluirId);
}
