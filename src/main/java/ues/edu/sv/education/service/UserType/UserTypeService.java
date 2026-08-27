package ues.edu.sv.education.service.UserType;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import ues.edu.sv.education.model.dto.userType.UserTypeResponseDto;
import ues.edu.sv.education.model.mappers.UserTypeMapper;
import ues.edu.sv.education.repository.UserRepository;
import ues.edu.sv.education.repository.UserTypeRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserTypeService {
    private final UserRepository userRepository;
    private final UserTypeRepository userTypeRepository;

    @Tool(description = "obtiene una lista de todos los tipos de usario permitidos por el sistema")
    public List<UserTypeResponseDto> getAllUserType() {
        return userTypeRepository.findAll().stream().map(UserTypeMapper::toDto).toList();
    }

}
