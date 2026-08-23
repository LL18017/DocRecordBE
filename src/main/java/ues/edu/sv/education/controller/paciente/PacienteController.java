package ues.edu.sv.education.controller.paciente;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.paciente.PacienteRequestDto;
import ues.edu.sv.education.model.dto.paciente.PacienteResponseDto;
import ues.edu.sv.education.service.paciente.PacienteService;

import java.util.List;

// ADMIN/MEDICO/ENFERMERA, no "cualquier autenticado": el catalogo de roles
// ya incluye PACIENTE, y listar aqui expone nombres, DUI, fecha de
// nacimiento y tipo de sangre de terceros -en un expediente clinico eso es
// fuga de datos de salud, no un descuido menor. Enfermeria entra porque
// toma signos vitales y registra pacientes; excluirla obligaria a que un
// medico dé de alta cada paciente, que no es como funciona una clinica
// (distinto del caso de clinicas: alli enfermeria trabaja pero no
// administra). Cuando exista el portal del paciente (ver el paciente
// consultando SU PROPIO expediente) va en un endpoint aparte resuelto por
// SecurityContext, igual que /clinics/mias -no forzarlo en este listado.
@Slf4j
@RestController
@RequestMapping("/pacientes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MEDICO','ENFERMERA')")
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
