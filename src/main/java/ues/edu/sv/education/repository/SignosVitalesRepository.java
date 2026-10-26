package ues.edu.sv.education.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
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
     *
     * Solo cuenta la ultima ACTIVA: una toma anulada sigue en el historico
     * pero no es "la ultima" (HU-16 criterio 4), ni la ficha ni la consulta
     * deben apoyarse en un valor que enfermeria declaro invalido.
     */
    Optional<SignosVitales> findFirstByPaciente_PersonaIdAndEstadoOrderByTomadoEnDesc(
            Long pacienteId, String estado);

    /**
     * La toma con bloqueo de escritura, para anularla.
     *
     * Sin el bloqueo, dos anulaciones simultaneas leerian ambas ACTIVA y la
     * segunda pisaria el motivo y la autoria de la primera: justo la huella que
     * la anulacion existe para conservar.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SignosVitales s where s.signosVitalesId = :id")
    Optional<SignosVitales> buscarParaAnular(@Param("id") Long id);
}
