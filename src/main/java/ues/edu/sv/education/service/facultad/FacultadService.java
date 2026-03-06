package ues.edu.sv.education.service.facultad;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ues.edu.sv.education.entity.Carrera;
import ues.edu.sv.education.entity.Facultad;
import ues.edu.sv.education.repository.CarreraRepository;
import ues.edu.sv.education.repository.FacultadRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FacultadService {
    private final FacultadRepository facultadRepository;
    private final CarreraRepository carreraRepository;

    public List<Facultad> getAll() {
        return facultadRepository.findAll();
    }

    public Facultad createFacultad(Facultad facultad) {
        return facultadRepository.save(facultad);
    }

    public Facultad setCarrera(Integer facultadId, Integer carreraId) {
        Facultad facultad = facultadRepository.findById(facultadId).orElseThrow(() ->
                new EntityNotFoundException("No se encuentra la facultad con el id " + facultadId)
        );
        Carrera carrera = carreraRepository.findById(carreraId).orElseThrow(() ->
                new EntityNotFoundException("No se encuentra la carrera con el id " + carreraId));

        if (facultad.getCarreras().contains(carrera)){
            throw new IllegalStateException("Ya existe esta carrera");
        }
        facultad.getCarreras().add(carrera);
        return facultadRepository.save(facultad);
    }

    public Facultad updateFacultad(Facultad facultad) {
        return facultadRepository.save(facultad);
    }

    public void deleteFacultad(Facultad facultad) {
        facultadRepository.delete(facultad);
    }

}
