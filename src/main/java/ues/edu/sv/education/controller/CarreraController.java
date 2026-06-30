package ues.edu.sv.education.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.Carrera.CarreraDto;
import ues.edu.sv.education.model.dto.Carrera.CarreraRequestDto;
import ues.edu.sv.education.service.facultad.CarreraService;

import java.util.List;

@RestController
@RequestMapping("/carrera")
@RequiredArgsConstructor
public class CarreraController {
    private final CarreraService carreraService;


    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping()
    public ResponseEntity<List<CarreraDto>> getAll() {
        return ResponseEntity.ok(carreraService.getAll().stream().map(CarreraDto::new).toList());
    }

    @PostMapping()

    public ResponseEntity<CarreraDto> createFacultad(@RequestBody CarreraRequestDto carrera) {
        return ResponseEntity.ok(carreraService.createCarrera(carrera.toEntity()).toDto());
    }
}
