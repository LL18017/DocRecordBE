package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ues.edu.sv.education.model.entity.Event;

import java.util.Collection;
import java.util.List;

public interface EventRepository extends JpaRepository<Event,Integer> {
    @Query("SELECT e FROM Event e WHERE e.eventType.eventTypeId = :type and e.eventStatus.eventStatusId=:status")
    List<Event> getEventByType(Integer type,Integer status);

    /**
     * Los eventos de un tipo cuya referencia esta en la lista, del mas reciente
     * al mas antiguo. Sirve para saber cual fue el ULTIMO envio de cada receta
     * (HU-25, criterio 4): quien llama se queda con el primero de cada ref.
     */
    @Query("""
        SELECT e FROM Event e JOIN FETCH e.eventStatus
        WHERE e.eventType.eventTypeId = :tipo AND e.ref IN :refs
        ORDER BY e.createdAt DESC, e.eventId DESC
    """)
    List<Event> delTipoConReferencias(@Param("tipo") Integer tipo, @Param("refs") Collection<String> refs);
}
