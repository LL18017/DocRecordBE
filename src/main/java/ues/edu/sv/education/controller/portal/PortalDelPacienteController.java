package ues.edu.sv.education.controller.portal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ues.edu.sv.education.model.dto.portal.MiExpedienteDto;
import ues.edu.sv.education.service.portal.PortalDelPacienteService;

/**
 * Portal del paciente (HU-34): el paciente consultando SU PROPIO expediente.
 *
 * Es el "endpoint aparte resuelto por SecurityContext" que anunciaba el
 * comentario de PacienteController, igual que /clinics/mias: /pacientes sigue
 * siendo del personal clinico y no se abre al rol PACIENTE.
 *
 * ── Por que /mi y sin ids ─────────────────────────────────────────────────
 * Ninguna ruta de aqui recibe el id del paciente, ni en la URL ni en la
 * consulta: es siempre el de la sesion (PacienteAutenticado). Un parametro
 * pacienteId que alguien agregue a mano se ignora, porque nadie lo lee. Asi el
 * criterio 2 -pedir el expediente de otro da 403- no depende de comparar dos
 * numeros en cada metodo: los endpoints que reciben un id (/pacientes,
 * /consultas, /alergias, /prescripciones...) siguen cerrados a este rol, y
 * responden 403.
 *
 * ── Solo GET ──────────────────────────────────────────────────────────────
 * Criterio 4: el portal es de solo lectura. No hay POST, PUT, PATCH ni DELETE
 * en este controlador, asi que cualquiera de ellos sobre /mi responde 405; y
 * los endpoints que si escriben datos clinicos no admiten el rol PACIENTE.
 * Si el paciente ve un dato equivocado, la correccion la hace el personal.
 */
@RestController
@RequestMapping("/mi")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PACIENTE')")
@Tag(name = "Portal del paciente", description = "HU-34: el expediente propio, en solo lectura")
public class PortalDelPacienteController {

    private final PortalDelPacienteService portalService;

    @Operation(
            summary = "Mi expediente",
            description = "El expediente del paciente de la sesion: datos personales, alergias vigentes, ultimos "
                    + "signos vitales, consultas y recetas (las anuladas incluidas y marcadas), proximas citas y "
                    + "el glosario de terminos. Queda registrado en la bitacora de accesos como VISTA. "
                    + "403 si la cuenta no es de paciente o no esta vinculada a un expediente."
    )
    @GetMapping("/expediente")
    public ResponseEntity<MiExpedienteDto> miExpediente() {
        return ResponseEntity.ok(portalService.miExpediente());
    }

    @Operation(
            summary = "Mi expediente en PDF",
            description = "El mismo expediente, con el glosario al final. Queda registrado en la bitacora de "
                    + "accesos como PDF. 403 en los mismos casos."
    )
    @GetMapping(value = "/expediente/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> miExpedienteEnPdf() {
        PortalDelPacienteService.ExpedienteEnPdf pdf = portalService.miExpedienteEnPdf();
        // attachment y no inline como la receta: lo que se pide aqui es
        // "descargar mi expediente" (criterio 5), no abrirlo para imprimir.
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"expediente-" + pdf.expediente() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.contenido());
    }
}
