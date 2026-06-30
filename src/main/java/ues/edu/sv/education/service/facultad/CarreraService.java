package ues.edu.sv.education.service.facultad;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ues.edu.sv.education.model.entity.Carrera;
import ues.edu.sv.education.repository.CarreraRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CarreraService {
    private final CarreraRepository carreraRepository;

    public List<Carrera> getAll(){
        return carreraRepository.findAll();
    }
    public Carrera createCarrera(Carrera carrera){
        return carreraRepository.save(carrera);
    }
    public Carrera createCarreSra(Carrera carrera){
        return carreraRepository.save(carrera);
    }
    public  Carrera updateFacultad(Carrera carrera){
        return carreraRepository.save(carrera);
    }
    public void deleteFacultad(Carrera facultad){
        carreraRepository.delete(facultad);
    }

}
