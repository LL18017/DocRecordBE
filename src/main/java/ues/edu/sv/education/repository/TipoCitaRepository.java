package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ues.edu.sv.education.model.entity.TipoCita;

@Repository
public interface TipoCitaRepository extends JpaRepository<TipoCita, Integer> {
}