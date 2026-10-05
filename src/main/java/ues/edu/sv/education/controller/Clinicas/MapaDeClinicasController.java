package ues.edu.sv.education.controller.Clinicas;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ues.edu.sv.education.model.dto.clinicas.ClinicaPublicaDto;
import ues.edu.sv.education.service.Clinicas.MapaDeClinicasService;

import java.time.Duration;
import java.util.List;

/**
 * Mapa publico de la red de clinicas (HU-28, DRS-96).
 *
 * Es el unico endpoint de /clinics que no pide sesion: lo consume la pagina
 * /mapa, que abre un paciente o un visitante que todavia no tiene cuenta. Por
 * eso vive en su propio controlador, con su ruta como RequestMapping de la
 * clase: BasicConfiguration, JwtFilter y ConfiguracionDeSwagger.esPublico
 * deciden lo publico por la ruta, y asi la ruta publica y la del portal no
 * comparten clase ni se pueden confundir al agregar un metodo.
 *
 * Solo lectura. Que GET sea publico no abre nada mas: cualquier otro verbo
 * sobre esta ruta sigue cayendo en la regla general, que exige token.
 */
@RestController
@RequestMapping("/clinics/publicas")
@RequiredArgsConstructor
@Tag(name = "Mapa de clínicas", description = "Directorio público de las clínicas activas de la red")
public class MapaDeClinicasController {

    /**
     * Cuanto puede guardar la respuesta el navegador o un proxy. Un minuto
     * basta para que moverse por la pagina o volver a ella no repita la
     * peticion, y es poco frente a lo que tarda en importar que una sede
     * recien dada de alta aparezca. La respuesta es la misma para todos, sin
     * sesion, asi que puede ser publica.
     */
    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic();

    private final MapaDeClinicasService mapa;

    @Operation(
            summary = "Clínicas activas de la red",
            description = "Las clínicas ACTIVAS con lo necesario para ubicarlas en el mapa: nombre, "
                    + "coordenadas, departamento, municipio, dirección, teléfono y horario. Nunca "
                    + "devuelve las inactivas ni quién registró cada clínica. Las que aún no tienen "
                    + "coordenadas se incluyen con latitud y longitud en null."
    )
    @GetMapping
    public ResponseEntity<List<ClinicaPublicaDto>> listar(
            @Parameter(description = "Solo las de este departamento. Ignora tildes y mayúsculas.",
                    example = "Santa Ana")
            @RequestParam(name = "departamento", required = false) String departamento
    ) {
        return ResponseEntity.ok()
                .cacheControl(CACHE)
                .body(mapa.listarActivas(departamento));
    }
}
