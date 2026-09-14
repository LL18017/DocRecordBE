package ues.edu.sv.education.controller.enfermera;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.enfermera.EnfermeraRequestDto;
import ues.edu.sv.education.model.dto.enfermera.EnfermeraResponseDto;
import ues.edu.sv.education.service.enfermera.EnfermeraService;

import java.util.List;

/**
 * Personal de enfermeria (epica: expediente clinico).
 *
 * Este controlador tapa un hueco que existia desde el principio: el rol
 * ENFERMERA aparecia en los @PreAuthorize de medio sistema y en la tabla
 * `role`, pero NINGUN camino lo creaba -ni endpoint, ni data.sql, ni
 * migracion-. Era un rol inalcanzable, y se noto al construir signos vitales,
 * que es la primera funcion que de verdad necesita una enfermera.
 *
 * ── Quien puede que ────────────────────────────────────────────────────────
 * DAR DE ALTA y DAR DE BAJA: solo ADMIN. Quien registra en el expediente
 * clinico tiene que haber sido admitido por alguien que responde por ello; por
 * eso esto no cuelga de /auth/register, que es publico.
 *
 * LEER: ADMIN y MEDICO. El medico lee las constantes firmadas por una
 * enfermera y necesita poder saber quien es; negarle el listado lo dejaria con
 * un nombre suelto al pie de un dato clinico.
 */
@Slf4j
@RestController
@RequestMapping("/enfermeras")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MEDICO')")
@Tag(name = "Enfermeria", description = "Alta y consulta del personal de enfermeria")
public class EnfermeraController {

    private final EnfermeraService enfermeraService;

    @Operation(
            summary = "Dar de alta a una enfermera",
            description = "Crea de una sola vez la persona, su registro en enfermeria y la cuenta "
                    + "de acceso con rol ENFERMERA, ya habilitada. "
                    + "409 si el correo de acceso ya existe o si la persona ya es enfermera; "
                    + "404 si se manda un personaId que no existe."
    )
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EnfermeraResponseDto> crear(
            @Valid @RequestBody EnfermeraRequestDto request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(enfermeraService.crear(request));
    }

    @Operation(summary = "Listar el personal de enfermeria")
    @GetMapping
    public ResponseEntity<List<EnfermeraResponseDto>> listar() {
        return ResponseEntity.ok(enfermeraService.listar());
    }

    @Operation(summary = "Ver una enfermera", description = "404 si no existe.")
    @GetMapping("/{personaId}")
    public ResponseEntity<EnfermeraResponseDto> obtener(@PathVariable("personaId") Long personaId) {
        return ResponseEntity.ok(enfermeraService.obtener(personaId));
    }

    @Operation(
            summary = "Dar de baja a una enfermera",
            description = "La marca inactiva y apaga su cuenta; NO la borra. Sus tomas de signos "
                    + "vitales la referencian, y borrarla dejaria constantes sin responsable: "
                    + "una baja laboral no puede reescribir el historial clinico."
    )
    @DeleteMapping("/{personaId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EnfermeraResponseDto> darDeBaja(@PathVariable("personaId") Long personaId) {
        return ResponseEntity.ok(enfermeraService.darDeBaja(personaId));
    }
}
