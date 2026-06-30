package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ues.edu.sv.education.model.entity.Role;

public interface RoleRepository extends JpaRepository<Role,Integer> {
}
