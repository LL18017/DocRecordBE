package ues.edu.sv.education.service.auth;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ues.edu.sv.education.entity.User;
import ues.edu.sv.education.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository repository;

    public List<User> getAllUser() {
        return repository.findAll();
    }

    public User getUser(String email) {
        return repository.findByEmailContainingIgnoreCase(email).orElseThrow(
                () -> new EntityNotFoundException("No se encontro al usuario")
        );
    }

}
