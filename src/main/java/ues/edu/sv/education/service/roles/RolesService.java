package ues.edu.sv.education.service.roles;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import ues.edu.sv.education.model.dto.roles.RoleRequestDto;
import ues.edu.sv.education.model.dto.roles.RoleResponseDto;
import ues.edu.sv.education.model.mappers.RoleMapper;
import ues.edu.sv.education.model.entity.Role;
import ues.edu.sv.education.repository.RoleRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RolesService {

    private final RoleRepository roleRepository;

    @Tool(description = "Obtiene una lista de todos los roles que puede tener un paciente")
    public List<RoleResponseDto> getAllRoles() {
        return roleRepository.findAll()
                .stream()
                .map(RoleMapper::toDto)
                .toList();
    }

    @Tool(description = "Obtiene un rol por su identificador")
    public RoleResponseDto getRoleById(Integer id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró el rol con ID: " + id));

        return RoleMapper.toDto(role);
    }

    @Tool(description = "Crea un nuevo rol")
    public RoleResponseDto createRole(RoleRequestDto dto) {
        Role role = RoleMapper.toEntity(dto);

        Role savedRole = roleRepository.save(role);

        return RoleMapper.toDto(savedRole);
    }

    @Tool(description = "Edita un rol existente")
    public RoleResponseDto updateRole(Integer id, RoleRequestDto dto) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró el rol con ID: " + id));

        role.setName(dto.getName());

        Role updatedRole = roleRepository.save(role);

        return RoleMapper.toDto(updatedRole);
    }

    @Tool(description = "Elimina un rol por su identificador")
    public void deleteRole(Integer id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró el rol con ID: " + id));

        roleRepository.delete(role);
    }
}