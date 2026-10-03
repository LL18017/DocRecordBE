package ues.edu.sv.education.controller.signosvitales;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.common.PaginaDto;
import ues.edu.sv.education.model.dto.signosvitales.SignosVitalesRequestDto;
import ues.edu.sv.education.model.dto.signosvitales.SignosVitalesResponseDto;
import ues.edu.sv.education.service.signosvitales.SignosVitalesService;

/**
 * Signos vitales: el triage previo a la consulta.
 *
 * ── Quien puede que ────────────────────────────────────────────────────────
 * REGISTRAR: solo ENFERMERA. Ni el medico ni el administrador, y es
 * deliberado. Tomar constantes es un acto propio de enfermeria, y el valor de
 * ese registro para quien lo lee despues esta en saber quien lo tomo. Un
 * medico que ademas ejerce enfermeria -que no es el caso del dominio- tendria
 * su fila en `enfermeras` y entraria por ahi; el servicio exige la fila, no la
 * etiqueta del token.
 *
 * LEER: ADMIN, MEDICO y ENFERMERA. Esta es la razon de ser del modulo: el
 * medico necesita las constantes ANTES de diagnosticar. Un signo vital que el
 * medico no puede leer no sirve para nada, igual que una receta que la
 * enfermera no puede leer (ver PrescripcionController).
 *
 * No hay edicion ni borrado. Una constante mal tomada no se corrige
 * reescribiendola: se toma otra vez, y ambas quedan en el historico con su
 * hora. Un expediente clinico es un registro de lo que paso, no el estado
 * actual de un formulario.
 */
@Slf4j
@RestController
@RequestMapping("/signos-vitales")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MEDICO','ENFERMERA')")
@Tag(name = "Signos vitales", description = "Constantes tomadas por enfermeria antes de la consulta")
public class SignosVitalesController {

    private final SignosVitalesService signosVitalesService;

    @Operation(
            summary = "Registrar una toma de constantes",
            description = "La firma la enfermera autenticada, no un enfermeraId del cuerpo. "
                    + "403 si quien opera no esta registrado como enfermeria; "
                    + "404 si el paciente o la consulta no existen; "
                    + "400 si no viene ni una sola medida o alguna cae fuera de rango fisiologico."
    )
    @PostMapping
    @PreAuthorize("hasRole('ENFERMERA')")
    public ResponseEntity<SignosVitalesResponseDto> crear(
            @Valid @RequestBody SignosVitalesRequestDto request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(signosVitalesService.crear(request));
    }

    @Operation(
            summary = "Listar tomas de constantes",
            description = "De la toma mas reciente a la mas antigua, paginado. "
                    + "Con pacienteId devuelve el historico de ese paciente, y da 404 si no existe: "
                    + "ahi el paciente es el recurso que se pide, no un filtro de busqueda. "
                    + "Sin pacienteId devuelve todas, que es lo que necesita la lista de trabajo "
                    + "del turno de enfermeria."
    )
    @GetMapping
    public ResponseEntity<PaginaDto<SignosVitalesResponseDto>> listar(
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false, defaultValue = "0") Integer pagina,
            @RequestParam(required = false, defaultValue = "20") Integer tamano
    ) {
        return ResponseEntity.ok(signosVitalesService.listar(pacienteId, pagina, tamano));
    }

    @Operation(
            summary = "Ultima toma del paciente",
            description = "Lo que el medico mira antes de diagnosticar, y lo que pinta la tarjeta "
                    + "«Ultimos Signos Vitales» del expediente. Responde 204 sin cuerpo cuando el "
                    + "paciente existe pero aun no tiene ninguna toma -que es el estado normal de "
                    + "un paciente recien registrado, no un error."
    )
    @GetMapping("/ultima")
    public ResponseEntity<SignosVitalesResponseDto> ultima(@RequestParam Long pacienteId) {
        SignosVitalesResponseDto ultima = signosVitalesService.ultimaDelPaciente(pacienteId);
        return ultima == null
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(ultima);
    }

    @Operation(summary = "Ver una toma", description = "404 si no existe.")
    @GetMapping("/{signosVitalesId}")
    public ResponseEntity<SignosVitalesResponseDto> obtener(
            @PathVariable("signosVitalesId") Long signosVitalesId) {
        return ResponseEntity.ok(signosVitalesService.obtener(signosVitalesId));
    }
}
