package ues.edu.sv.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ues.edu.sv.education.model.entity.Medico;
import ues.edu.sv.education.repository.proyeccion.EspecialidadDePersona;

import java.util.List;

public interface MedicoRepository extends JpaRepository<Medico, Long> {

    /**
     * La especialidad de varios medicos en una sola consulta.
     *
     * HU-05 criterio 1 pide que el listado de usuarios muestre "nombre, correo,
     * rol, ESPECIALIDAD y estado". La especialidad esta en `medicos`, no en
     * `users`, asi que hay que ir a buscarla; hacerlo fila por fila serian
     * tantas consultas como usuarios tenga la pagina.
     *
     * El JOIN es interno a proposito: quien no tiene fila en `medicos` -- una
     * enfermera, un administrador que no ejerce -- simplemente no aparece en el
     * resultado, y el llamador lo interpreta como "sin especialidad". Es lo
     * correcto: no es que le falte el dato, es que la pregunta no le aplica.
     */
    @Query("""
            SELECT new ues.edu.sv.education.repository.proyeccion.EspecialidadDePersona(
                       m.persona.personaId, e.nombre)
            FROM Medico m
            JOIN m.especialidad e
            WHERE m.persona.personaId IN :personaIds
            """)
    List<EspecialidadDePersona> especialidadesDe(@Param("personaIds") List<Long> personaIds);
}
