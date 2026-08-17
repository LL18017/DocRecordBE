package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ues.edu.sv.education.model.entity.EventType;

public interface EventTypeRepository extends JpaRepository<EventType,Integer> {
}
