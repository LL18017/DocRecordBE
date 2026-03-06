package ues.edu.sv.education.controller;

import jakarta.websocket.server.PathParam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.dto.Carrera.CarreraDto;
import ues.edu.sv.education.dto.Carrera.CarreraRequestDto;
import ues.edu.sv.education.dto.Facultad.FacultadDto;
import ues.edu.sv.education.dto.Facultad.FacultadRequestDto;
import ues.edu.sv.education.service.facultad.CarreraService;
import ues.edu.sv.education.service.facultad.FacultadService;

import java.util.List;

@RestController
@RequestMapping("/carrera")
@RequiredArgsConstructor
public class CarreraController {
    private final CarreraService carreraService;


    @GetMapping()
    public ResponseEntity<List<CarreraDto>> getAll() {
        return ResponseEntity.ok(carreraService.getAll().stream().map(CarreraDto::new).toList());
    }

    @PostMapping()
    public ResponseEntity<CarreraDto> createFacultad(@RequestBody CarreraRequestDto carrera) {
        return ResponseEntity.ok(carreraService.createCarrera(carrera.toEntity()).toDto());
    }
}
