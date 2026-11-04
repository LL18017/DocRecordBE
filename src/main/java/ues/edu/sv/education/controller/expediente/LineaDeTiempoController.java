package ues.edu.sv.education.controller.expediente;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ues.edu.sv.education.model.dto.expediente.EventoDelExpedienteDto;
import ues.edu.sv.education.service.expediente.LineaDeTiempoService;

import java.util.List;

/**
 * Expediente clinico consolidado (HU-15, DRS-87).
 *
 * Los mismos roles que leen cada pieza por separado: ADMIN, MEDICO y
 * ENFERMERA. Lo que enfermeria no ve de la consulta -el diagnostico- se
 * recorta en ConsultaService, igual que en /consultas.
 *
 * Solo GET: la linea de tiempo es de solo lectura (criterio 5). Cada registro
 * se corrige o se anula desde su propia pantalla.
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MEDICO','ENFERMERA')")
@Tag(name = "Expediente", description = "Linea de tiempo del expediente clinico")
public class LineaDeTiempoController {

    private final LineaDeTiempoService lineaDeTiempoService;

    @Operation(
            summary = "Linea de tiempo del expediente",
            description = "Consultas, tomas de signos vitales y recetas del paciente en una sola lista, de la mas "
                    + "reciente a la mas antigua, de todas las sedes de la red. Cada evento dice en que clinica y "
                    + "que profesional lo registro; los anulados aparecen con su estado. Para enfermeria la "
                    + "consulta llega sin el diagnostico. 404 si el paciente no existe."
    )
    @GetMapping("/pacientes/{pacienteId}/linea-de-tiempo")
    public ResponseEntity<List<EventoDelExpedienteDto>> lineaDeTiempo(@PathVariable Long pacienteId) {
        return ResponseEntity.ok(lineaDeTiempoService.delPaciente(pacienteId));
    }
}
