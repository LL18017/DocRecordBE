package ues.edu.sv.education.service.roles;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import ues.edu.sv.education.model.dto.roles.RoleDto;
import ues.edu.sv.education.model.mappers.RoleMapper;
import ues.edu.sv.education.repository.RoleRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RolesService {
    private final RoleRepository roleRepository;

    @Tool(description = "obtiene una lista de todos los roles que puede tener un paciente")
    public List<RoleDto> getAllRoles() {
        return roleRepository.findAll().stream().map(RoleMapper::toDto).toList();
    }
}
