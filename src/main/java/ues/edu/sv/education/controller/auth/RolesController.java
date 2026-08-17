package ues.edu.sv.education.controller.auth;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ues.edu.sv.education.model.dto.roles.RoleDto;
import ues.edu.sv.education.service.roles.RolesService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RolesController {
    private final RolesService roleService;
    @GetMapping("/all")
    public ResponseEntity<List<RoleDto>> getAll(
    ) {
        return ResponseEntity.ok(roleService.getAll());
    }
}
