package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ues.edu.sv.education.model.entity.UserType;

public interface UserTypeRepository extends JpaRepository<UserType, Integer> {
}
