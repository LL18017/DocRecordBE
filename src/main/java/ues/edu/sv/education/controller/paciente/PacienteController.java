package ues.edu.sv.education.controller.paciente;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.paciente.PacienteRequestDto;
import ues.edu.sv.education.model.dto.paciente.PacienteResponseDto;
import ues.edu.sv.education.service.paciente.PacienteService;

import java.util.List;

// Sin @PreAuthorize a proposito: a diferencia de clinicas o /user, nadie
// definio todavia que roles pueden registrar o consultar pacientes (solo
// exige autenticacion, via JwtFilter). Lo marco en vez de inventar una
// regla -ver el mensaje al equipo.
@Slf4j
@RestController
@RequestMapping("/pacientes")
@RequiredArgsConstructor
@Tag(name = "Pacientes", description = "Alta y busqueda de pacientes")
public class PacienteController {

    private final PacienteService pacienteService;

    @Operation(
            summary = "Registrar paciente",
            description = "Acepta persona.personaId para reutilizar una identidad existente, o sin el para crear una nueva. " +
                    "422 si a la persona le falta fecha de nacimiento o sexo tras aplicar lo recibido; 409 si ya es paciente."
    )
    @PostMapping
    public ResponseEntity<PacienteResponseDto> crear(@Valid @RequestBody PacienteRequestDto request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(pacienteService.crear(request));
    }

    @Operation(
            summary = "Listar/buscar pacientes",
            description = "Una sola caja de busqueda que coincide contra apellidos, nombres o DUI. Sin parametro devuelve todos."
    )
    @GetMapping
    public ResponseEntity<List<PacienteResponseDto>> buscar(
            @RequestParam(required = false) String buscar
    ) {
        return ResponseEntity.ok(pacienteService.buscar(buscar));
    }
}
