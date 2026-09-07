package ues.edu.sv.education.service.UserType;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import ues.edu.sv.education.model.dto.userType.UserTypeRequestDto;
import ues.edu.sv.education.model.dto.userType.UserTypeResponseDto;
import ues.edu.sv.education.model.mappers.UserTypeMapper;
import ues.edu.sv.education.model.entity.UserType;
import ues.edu.sv.education.repository.UserRepository;
import ues.edu.sv.education.repository.UserTypeRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserTypeService {

    private final UserRepository userRepository;
    private final UserTypeRepository userTypeRepository;

    @Tool(description = "Obtiene una lista de todos los tipos de usuario permitidos por el sistema")
    public List<UserTypeResponseDto> getAllUserType() {
        return userTypeRepository.findAll()
                .stream()
                .map(UserTypeMapper::toDto)
                .toList();
    }

    @Tool(description = "Obtiene un tipo de usuario por su identificador")
    public UserTypeResponseDto getUserTypeById(Integer id) {
        UserType userType = userTypeRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("No se encontró el tipo de usuario con ID: " + id));

        return UserTypeMapper.toDto(userType);
    }

    @Tool(description = "Crea un nuevo tipo de usuario")
    public UserTypeResponseDto createUserType(UserTypeRequestDto dto) {

        UserType userType = UserTypeMapper.toEntity(dto);

        UserType savedUserType = userTypeRepository.save(userType);

        return UserTypeMapper.toDto(savedUserType);
    }

    @Tool(description = "Edita un tipo de usuario existente")
    public UserTypeResponseDto updateUserType(
            Integer id,
            UserTypeRequestDto dto
    ) {

        UserType userType = userTypeRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("No se encontró el tipo de usuario con ID: " + id));

        userType.setName(dto.name());

        UserType updatedUserType = userTypeRepository.save(userType);

        return UserTypeMapper.toDto(updatedUserType);
    }

    @Tool(description = "Elimina un tipo de usuario por su identificador")
    public void deleteUserType(Integer id) {

        UserType userType = userTypeRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("No se encontró el tipo de usuario con ID: " + id));

        userTypeRepository.delete(userType);
    }
}