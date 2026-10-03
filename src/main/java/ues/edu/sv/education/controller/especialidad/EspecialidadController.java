package ues.edu.sv.education.controller.especialidad;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ues.edu.sv.education.model.dto.especialidad.EspecialidadResponseDto;
import ues.edu.sv.education.service.especialidad.EspecialidadService;

import java.util.List;

@RestController
@RequestMapping("/especialidades")
@RequiredArgsConstructor
@Tag(name = "Especialidades", description = "Catalogo de especialidades medicas")
public class EspecialidadController {

    private final EspecialidadService especialidadService;

    @Operation(
            summary = "Listar especialidades activas",
            description = "Catalogo que consume el formulario de registro del frontend"
    )
    @GetMapping
    public ResponseEntity<List<EspecialidadResponseDto>> listar() {
        return ResponseEntity.ok(especialidadService.listarActivas());
    }
}
