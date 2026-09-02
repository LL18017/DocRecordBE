package ues.edu.sv.education.service.auth;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import ues.edu.sv.education.model.dto.User.UserResponseDto;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.mappers.UserMapper;
import ues.edu.sv.education.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAuthService {
    private final UserRepository repository;

    @Tool(description = "obtiene una lista de todos los usarios independientemente su rol ademas de informacion importante sobre estos")
    public List<UserResponseDto> getAllUser() {

        System.out.println("TOOL >>>  getAllUser EJECUTADA");
        return repository.findAll().stream().map(UserMapper::toDto).toList();
    }

    public User getUser(String email) {
        System.out.println("TOOL >>>  getUser EJECUTADA");
        return repository.findByEmailContainingIgnoreCase(email).orElseThrow(
                () -> new EntityNotFoundException("No se encontro al usuario")
        );
    }

}
