package ues.edu.sv.education.controller.persona;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ues.edu.sv.education.model.dto.persona.PersonaConRolesResponseDto;
import ues.edu.sv.education.service.persona.PersonaService;

@Slf4j
@RestController
@RequestMapping("/personas")
@RequiredArgsConstructor
@Tag(name = "Personas", description = "Identidad base compartida por medicos, enfermeras y pacientes")
public class PersonaController {

    private final PersonaService personaService;

    @Operation(
            summary = "Buscar persona por DUI",
            description = "Devuelve la persona y que papeles clinicos ya tiene (esMedico, esEnfermera, esPaciente). 404 si no existe."
    )
    @GetMapping
    public ResponseEntity<PersonaConRolesResponseDto> buscarPorDui(@RequestParam String dui) {
        return ResponseEntity.ok(personaService.buscarPorDui(dui));
    }
}
