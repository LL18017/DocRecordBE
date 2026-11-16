package ues.edu.sv.education.controller.portal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import ues.edu.sv.education.model.dto.habitosestilodevida.HabitosEstiloDeVidaResponse;
import ues.edu.sv.education.model.dto.portal.MiPerfilDto;
import ues.edu.sv.education.model.dto.portal.PreferenciasDelPerfilRequest;
import ues.edu.sv.education.service.portal.MiPerfilService;

import java.util.List;

/**
 * "Mi perfil" del paciente (DEF-07: HU-33 c4, HU-14 c4).
 *
 * Solo el rol PACIENTE; cualquier otro recibe 403. Como /mi (portal), ninguna
 * ruta recibe un id: el paciente sale del token. Agregar este controlador no
 * abre al rol PACIENTE ningun otro endpoint: los clinicos siguen con sus
 * propios @PreAuthorize sin PACIENTE.
 *
 * Lo unico que se escribe es la preferencia de recordatorios (PUT o PATCH,
 * mismo efecto). Los habitos son de solo lectura y sin los anulados.
 */
@RestController
@RequestMapping("/api/mi-perfil")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PACIENTE')")
@SecurityRequirement(name = "jwt")
@Tag(name = "Portal del paciente", description = "HU-34: el expediente propio, en solo lectura")
public class MiPerfilController {

    private final MiPerfilService miPerfilService;

    @Operation(
            summary = "Mi perfil",
            description = "Nombre, correo, telefono y si recibe recordatorios de cita, del paciente de la sesion. "
                    + "403 si la cuenta no es de paciente o no esta vinculada a un expediente."
    )
    @GetMapping
    public ResponseEntity<MiPerfilDto> miPerfil() {
        return ResponseEntity.ok(miPerfilService.miPerfil());
    }

    @Operation(
            summary = "Cambiar mi preferencia de recordatorios",
            description = "Solo `recibeRecordatorios` (HU-33 c4). Con false deja de llegar el recordatorio "
                    + "automatico de cita; la confirmacion, la cancelacion y la reprogramacion se siguen "
                    + "enviando. 400 si falta el campo. PUT y PATCH hacen lo mismo."
    )
    @RequestMapping(method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ResponseEntity<MiPerfilDto> actualizarPreferencias(
            @RequestBody PreferenciasDelPerfilRequest request) {
        return ResponseEntity.ok(miPerfilService.actualizarPreferencias(request));
    }

    @Operation(
            summary = "Mis habitos",
            description = "Los habitos de estilo de vida ACTIVOS del paciente de la sesion, en solo lectura "
                    + "(HU-14 c4). Los anulados no se incluyen."
    )
    @GetMapping("/habitos")
    public ResponseEntity<List<HabitosEstiloDeVidaResponse>> misHabitos() {
        return ResponseEntity.ok(miPerfilService.misHabitos());
    }
}
