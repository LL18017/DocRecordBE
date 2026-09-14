package ues.edu.sv.education.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ues.edu.sv.education.model.entity.SignosVitales;

import java.util.Optional;

@Repository
public interface SignosVitalesRepository extends JpaRepository<SignosVitales, Long> {

    /**
     * El historico de un paciente, de la toma mas reciente a la mas antigua.
     * Es el orden en que se lee un expediente: lo ultimo primero.
     */
    Page<SignosVitales> findByPaciente_PersonaIdOrderByTomadoEnDesc(Long pacienteId, Pageable pageable);

    /**
     * Todas las tomas, de la mas reciente a la mas antigua.
     *
     * La usa la pantalla de enfermeria, que no es el expediente de UN paciente
     * sino la lista de trabajo del turno: lo ultimo que se tomo, sea de quien
     * sea. Por eso `pacienteId` es opcional en el endpoint.
     */
    Page<SignosVitales> findAllByOrderByTomadoEnDesc(Pageable pageable);

    /**
     * La ultima toma del paciente.
     *
     * Existe aparte del listado porque es la consulta que dispara la ficha
     * clinica al abrirse -la tarjeta "Ultimos Signos Vitales"- y pedir una
     * pagina entera para quedarse con el primer elemento seria traer de la
     * base lo que no se va a usar. El indice de V9 (paciente_id, tomado_en
     * DESC) esta puesto justo para esto.
     */
    Optional<SignosVitales> findFirstByPaciente_PersonaIdOrderByTomadoEnDesc(Long pacienteId);
}
