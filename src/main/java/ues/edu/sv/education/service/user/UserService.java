package ues.edu.sv.education.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.User.UserRequestDto;
import ues.edu.sv.education.model.dto.User.UserResponseDto;
import ues.edu.sv.education.model.entity.Role;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.mappers.UserMapper;
import ues.edu.sv.education.repository.RoleRepository;
import ues.edu.sv.education.repository.UserRepository;


import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public List<UserResponseDto> getAll(Integer inicio, Integer fin) {
        Pageable pageable = PageRequest.of(inicio, fin);
        return userRepository.findAll(pageable).stream().map(UserMapper::toDto).toList();
    }

    public UserResponseDto addRole(int userID, int roleID) {
        Optional<User> userOpt = userRepository.findById(userID);
        if (userOpt.isEmpty()) {
            throw new NoResourceFoundException("No se encontro el usuario con id: " + userID, "404");
        }
        Optional<Role> roleOpt = roleRepository.findById(roleID);
        if (roleOpt.isEmpty()) {
            throw new NoResourceFoundException("No se encontro el rol con id: " + roleID, "404");
        }
        User user = userOpt.get();
        Role role = roleOpt.get();
        if (user.getRoles().contains(role)) {
            throw new GeneralException("El usuario ya cuenta con este rol", "409");
        }
        user.getRoles().add(role);
        userRepository.save(user);
        return UserMapper.toDto(user);
    }

    public UserResponseDto createUser(UserRequestDto userRequest) {
        User user = UserMapper.toEntity(userRequest);
        userRepository.save(user);
        return UserMapper.toDto(user);
    }
    public void deleteUser(String userEmail) {
        User user = userRepository.findByEmailContainingIgnoreCase(userEmail).orElseThrow(()-> new NoResourceFoundException("Usuario no encontrado","404"));
        userRepository.delete(user);
    }
}
