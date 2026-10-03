package ues.edu.sv.education.service.roles;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.roles.RoleDto;
import ues.edu.sv.education.model.mappers.RoleMapper;
import ues.edu.sv.education.repository.RoleRepository;

import java.util.List;

// Las herramientas que ve el asistente de IA (AIService) son solo de lectura:
// el catalogo de roles es cerrado desde V7, asi que crear, editar o borrar un
// rol no es algo que el modelo deba poder hacer.
@Service
@RequiredArgsConstructor
public class RolesService {
    private final RoleRepository roleRepository;

    @Tool(description = "Obtiene una lista de todos los roles que puede tener un usuario")
    public List<RoleDto> getAll() {
        return roleRepository.findAll().stream().map(RoleMapper::toDto).toList();
    }

    @Tool(description = "Obtiene un rol por su identificador")
    public RoleDto getRoleById(Integer id) {
        return roleRepository.findById(id)
                .map(RoleMapper::toDto)
                .orElseThrow(() -> new NoResourceFoundException("No se encontró el rol con ID: " + id, "404"));
    }
}
