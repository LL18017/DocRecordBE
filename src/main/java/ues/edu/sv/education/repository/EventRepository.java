package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ues.edu.sv.education.model.entity.Event;

import java.util.List;

public interface EventRepository extends JpaRepository<Event,Integer> {
    @Query("SELECT e FROM Event e WHERE e.eventType.eventTypeId = :type and e.eventStatus.eventStatusId=:status")
    List<Event> getEventByType(Integer type,Integer status);
}
