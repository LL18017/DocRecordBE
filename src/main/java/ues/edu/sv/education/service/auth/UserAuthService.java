package ues.edu.sv.education.service.auth;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserAuthService {
    private final UserRepository repository;

    public User getUser(String email) {
        return repository.findByEmailContainingIgnoreCase(email).orElseThrow(
                () -> new EntityNotFoundException("No se encontro al usuario")
        );
    }

}
