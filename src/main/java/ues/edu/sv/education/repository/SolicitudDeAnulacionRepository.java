package ues.edu.sv.education.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ues.edu.sv.education.model.entity.SolicitudDeAnulacion;
import ues.edu.sv.education.model.enums.EstadoSolicitudDeAnulacion;

import java.util.List;
import java.util.Optional;

public interface SolicitudDeAnulacionRepository extends JpaRepository<SolicitudDeAnulacion, Long> {

    /**
     * La solicitud con bloqueo de escritura, para aprobarla o rechazarla.
     *
     * Dos administradores que aprueban a la vez leerian ambos "falta una" y
     * ninguno aplicaria la anulacion, o los dos la aplicarian. Con el bloqueo
     * el segundo espera y cuenta la aprobacion del primero.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudDeAnulacion s where s.solicitudId = :id")
    Optional<SolicitudDeAnulacion> buscarParaResolver(@Param("id") Long id);

    /** La solicitud en curso de una consulta (las de tipo CONSULTA). */
    @Query("""
    SELECT s FROM SolicitudDeAnulacion s
    WHERE s.consulta.consultaId = :consultaId
      AND s.tipo = ues.edu.sv.education.model.enums.TipoDocumentoAnulable.CONSULTA
      AND s.estado = ues.edu.sv.education.model.enums.EstadoSolicitudDeAnulacion.PENDIENTE
""")
    Optional<SolicitudDeAnulacion> pendienteDeConsulta(@Param("consultaId") Long consultaId);

    /** La solicitud en curso de una receta. */
    @Query("""
    SELECT s FROM SolicitudDeAnulacion s
    WHERE s.prescripcion.prescripcionId = :prescripcionId
      AND s.estado = ues.edu.sv.education.model.enums.EstadoSolicitudDeAnulacion.PENDIENTE
""")
    Optional<SolicitudDeAnulacion> pendienteDeReceta(@Param("prescripcionId") Long prescripcionId);

    /**
     * Las solicitudes en curso de las recetas de una consulta: se cancelan
     * cuando la consulta entera se anula, porque sus recetas se anulan con ella.
     */
    @Query("""
    SELECT s FROM SolicitudDeAnulacion s
    WHERE s.consulta.consultaId = :consultaId
      AND s.tipo = ues.edu.sv.education.model.enums.TipoDocumentoAnulable.RECETA
      AND s.estado = ues.edu.sv.education.model.enums.EstadoSolicitudDeAnulacion.PENDIENTE
""")
    List<SolicitudDeAnulacion> pendientesDeRecetasDe(@Param("consultaId") Long consultaId);

    /**
     * Listado con filtros opcionales: estado y quien la pidio. Un filtro null
     * no restringe (coalesce, por lo mismo que ConsultaRepository.buscar).
     */
    @Query("""
    SELECT s FROM SolicitudDeAnulacion s
    JOIN FETCH s.solicitadaPor u
    JOIN FETCH u.persona
    JOIN FETCH s.consulta c
    JOIN FETCH c.paciente pac
    JOIN FETCH pac.persona
    LEFT JOIN FETCH s.prescripcion
    WHERE s.estado = coalesce(:estado, s.estado)
      AND u.UserID = coalesce(:solicitadaPor, u.UserID)
    ORDER BY s.solicitadaEn DESC, s.solicitudId DESC
""")
    List<SolicitudDeAnulacion> buscar(
            @Param("estado") EstadoSolicitudDeAnulacion estado,
            @Param("solicitadaPor") Integer solicitadaPor);
}
