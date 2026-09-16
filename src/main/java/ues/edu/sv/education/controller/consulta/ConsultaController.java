package ues.edu.sv.education.controller.consulta;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.consulta.ConsultaActualizacionDto;
import ues.edu.sv.education.model.dto.consulta.ConsultaRequestDto;
import ues.edu.sv.education.model.dto.consulta.ConsultaResponseDto;
import ues.edu.sv.education.service.consulta.ConsultaService;

import java.util.List;

/**
 * Consultas medicas (epica E6).
 *
 * ── Quien puede que ────────────────────────────────────────────────────────
 * LEER: ADMIN, MEDICO y ENFERMERA, igual que /pacientes. Enfermeria necesita
 * ver la consulta para preparar al paciente, aplicar indicaciones y tomar
 * signos; negarle la lectura obligaria a que el medico le dicte de viva voz lo
 * que ya esta escrito.
 *
 * ESCRIBIR: solo ADMIN y MEDICO. Y aunque el endpoint deje pasar al ADMIN, el
 * servicio exige ademas una fila en `medicos` para crear una consulta y para
 * escribir un diagnostico -- de modo que un ADMIN que no ejerce puede corregir
 * el motivo o la sucursal de una consulta, pero no puede diagnosticar ni
 * inventarse una atencion. La anotacion decide quien entra; el servicio decide
 * quien firma.
 */
@Slf4j
@RestController
@RequestMapping("/consultas")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MEDICO','ENFERMERA')")
@Tag(name = "Consultas", description = "Registro y consulta de atenciones medicas")
public class ConsultaController {

    private final ConsultaService consultaService;

    @Operation(
            summary = "Registrar una consulta",
            description = "El medico que atiende es el usuario autenticado, no un dato del cuerpo. "
                    + "403 si quien pide no esta registrado como medico; 404 si el paciente o la clinica no existen. "
                    + "clinicaId es opcional."
    )
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MEDICO')")
    public ResponseEntity<ConsultaResponseDto> crear(@Valid @RequestBody ConsultaRequestDto request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(consultaService.crear(request));
    }

    @Operation(
            summary = "Listar consultas",
            description = "Con pacienteId devuelve su historial, de la mas reciente a la mas antigua. "
                    + "Sin parametro devuelve todas. 404 si el paciente no existe."
    )
    @GetMapping
    public ResponseEntity<List<ConsultaResponseDto>> listar(
            @RequestParam(required = false) Long pacienteId
    ) {
        return ResponseEntity.ok(consultaService.listar(pacienteId));
    }

    @Operation(summary = "Ver una consulta", description = "404 si no existe.")
    @GetMapping("/{consultaId}")
    public ResponseEntity<ConsultaResponseDto> obtener(@PathVariable("consultaId") Long consultaId) {
        return ResponseEntity.ok(consultaService.obtener(consultaId));
    }

    @Operation(
            summary = "Actualizar una consulta",
            description = "Completa sin destruir: un campo null o vacio significa 'no lo estoy tocando'. "
                    + "El diagnostico solo lo puede escribir un medico (403 en caso contrario). "
                    + "El paciente de una consulta no se puede cambiar."
    )
    @PutMapping("/{consultaId}")
    @PreAuthorize("hasAnyRole('ADMIN','MEDICO')")
    public ResponseEntity<ConsultaResponseDto> actualizar(
            @PathVariable("consultaId") Long consultaId,
            @Valid @RequestBody ConsultaActualizacionDto request) {
        return ResponseEntity.ok(consultaService.actualizar(consultaId, request));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Aqui vivia DELETE /consultas/{id}. Se retiro a proposito.
    //
    // HU-21 (DRS-91) lo dice sin margen: "el sistema NUNCA ejecuta un DELETE
    // sobre una consulta. Anular es un cambio de estado, y esa distincion es la
    // que hace defendible el expediente ante una auditoria".
    //
    // Lo que habia contradecia eso de tres formas a la vez:
    //
    //   · Borraba de verdad, y con ON DELETE CASCADE se llevaba las recetas
    //     emitidas. Una consulta equivocada OCURRIO: alguien la abrio, quedo
    //     constancia, y puede haberse entregado una receta a partir de ella.
    //     Hacerla desaparecer no corrige el error, lo esconde.
    //   · No comprobaba de quien era. ConsultaService.eliminar llamaba a delete
    //     sin mirar quien lo pedia, asi que cualquier MEDICO podia destruir la
    //     consulta de cualquier otro.
    //   · No pedia motivo. El criterio 1 exige uno de al menos 20 caracteres.
    //
    // La operacion que corresponde -anular con motivo, marcando tambien sus
    // recetas- es el trabajo de HU-21 y se implementa ahi. Mientras tanto, no
    // tener el endpoint es estrictamente mejor que tener uno que destruye
    // historia clinica sin dejar rastro.
    // ══════════════════════════════════════════════════════════════════════
}
