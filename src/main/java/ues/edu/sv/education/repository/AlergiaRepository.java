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
     * Las alergias vigentes del paciente que coinciden con un medicamento
     * (HU-24).
     *
     * Coincidir es CONTENCION DE TEXTO normalizado -minusculas, sin tildes y
     * sin espacios alrededor, con la misma sin_tildes() de V13-: la sustancia
     * esta dentro del principio activo o del nombre generico, o al reves.
     * Limitacion conocida y deliberada: no hay familias farmacologicas. Una
     * alergia a "Penicilina" NO avisa de "Amoxicilina" (es de la misma
     * familia, pero el texto no lo dice). Inventar esa tabla seria decidir
     * medicina por el medico; si el dia de manana se quiere, es un catalogo
     * clinico revisado y no un LIKE.
     */
    @Query(value = """
            SELECT a.* FROM alergias a
            JOIN medicamentos m ON m.medicamento_id = :medicamentoId
            WHERE a.paciente_id = :pacienteId
              AND a.eliminada_en IS NULL
              AND length(trim(a.sustancia)) > 0
              AND (
                    position(lower(public.sin_tildes(trim(a.sustancia)))
                             IN lower(public.sin_tildes(m.principio_activo))) > 0
                 OR position(lower(public.sin_tildes(trim(a.sustancia)))
                             IN lower(public.sin_tildes(m.nombre_generico))) > 0
                 OR (length(trim(m.principio_activo)) > 0
                     AND position(lower(public.sin_tildes(trim(m.principio_activo)))
                                  IN lower(public.sin_tildes(a.sustancia))) > 0)
                 OR (length(trim(m.nombre_generico)) > 0
                     AND position(lower(public.sin_tildes(trim(m.nombre_generico)))
                                  IN lower(public.sin_tildes(a.sustancia))) > 0)
              )
            ORDER BY a.alergia_id
            """, nativeQuery = true)
    List<Alergia> buscarCoincidentesConMedicamento(@Param("pacienteId") Long pacienteId,
                                                   @Param("medicamentoId") Long medicamentoId);

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
