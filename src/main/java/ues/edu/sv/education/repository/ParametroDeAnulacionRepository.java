package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ues.edu.sv.education.model.entity.ParametroDeAnulacion;
import ues.edu.sv.education.model.enums.TipoDocumentoAnulable;

public interface ParametroDeAnulacionRepository extends JpaRepository<ParametroDeAnulacion, TipoDocumentoAnulable> {
}
