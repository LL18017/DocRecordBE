package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ues.edu.sv.education.model.entity.Invitacion;
import ues.edu.sv.education.model.entity.User;

import java.util.List;
import java.util.Optional;

public interface InvitacionRepository extends JpaRepository<Invitacion, Long> {

    Optional<Invitacion> findByToken(String token);

    List<Invitacion> findByUsuarioAndUsadaFalse(User usuario);
}
