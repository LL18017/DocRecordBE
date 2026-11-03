package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ues.edu.sv.education.model.entity.AprobacionDeAnulacion;

import java.util.List;

public interface AprobacionDeAnulacionRepository
        extends JpaRepository<AprobacionDeAnulacion, AprobacionDeAnulacion.Clave> {

    // Consulta escrita a mano: el id de User se llama UserID, y el nombre
    // derivado (Usuario_UserID) no lo resuelve.
    @Query("""
    SELECT count(a) > 0 FROM AprobacionDeAnulacion a
    WHERE a.solicitud.solicitudId = :solicitudId AND a.usuario.UserID = :userId
""")
    boolean yaAprobo(@Param("solicitudId") Long solicitudId, @Param("userId") Integer userId);

    long countBySolicitud_SolicitudId(Long solicitudId);

    List<AprobacionDeAnulacion> findBySolicitud_SolicitudIdOrderByAprobadaEnAsc(Long solicitudId);
}
