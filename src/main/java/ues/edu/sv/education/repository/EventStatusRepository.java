package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ues.edu.sv.education.model.entity.EventStatus;

public interface EventStatusRepository extends JpaRepository<EventStatus,Integer> {
}
