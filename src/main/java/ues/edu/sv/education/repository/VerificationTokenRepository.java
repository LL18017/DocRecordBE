package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.entity.VerificationToken;

import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Integer> {
    Optional<VerificationToken> findByToken(String token);
    void deleteAllByUser(User user);
}