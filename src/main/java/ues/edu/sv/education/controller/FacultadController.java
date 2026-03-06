package ues.edu.sv.education.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.dto.Carrera.CarreraRequestDto;
import ues.edu.sv.education.dto.Facultad.FacultadDto;
import ues.edu.sv.education.dto.Facultad.FacultadRequestDto;
import ues.edu.sv.education.service.facultad.FacultadService;

import java.util.List;

@RestController
@RequestMapping("/facultad")
@RequiredArgsConstructor
public class FacultadController {
    private final FacultadService facultadService;


    @GetMapping()
    public ResponseEntity<List<FacultadDto>> getAll(){
        return ResponseEntity.ok(facultadService.getAll().stream().map(FacultadDto::new).toList());
    }
    @PostMapping()
    public ResponseEntity<FacultadDto> createFacultad(@Valid  @RequestBody FacultadRequestDto facultad){
        return ResponseEntity.ok(facultadService.createFacultad(facultad.toEntity()).toDto());
    }
    @PostMapping("/{facultadId}")
    public ResponseEntity<FacultadDto> setCarreraFacultad(@RequestBody CarreraRequestDto carrera,
                                                         @PathVariable Integer facultadId,
                                                         @RequestParam Integer carreraId) {
        return ResponseEntity.ok(facultadService.setCarrera(facultadId,carreraId).toDto());
    }
}
