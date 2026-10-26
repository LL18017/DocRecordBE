package ues.edu.sv.education.service.Clinicas;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.model.dto.clinicas.ClinicaPublicaDto;
import ues.edu.sv.education.model.entity.Clinicas;
import ues.edu.sv.education.repository.ClinicaRepository;

import java.util.List;

/**
 * El mapa publico de la red de clinicas (HU-28, DRS-96).
 *
 * Vive aparte de ClinicaService a proposito: aquel trabaja siempre con un
 * usuario autenticado (usuarioActual, exigirPropietarioOAdmin) y este no tiene
 * a nadie detras. Mezclarlos invitaria a que un cambio pensado para el portal
 * -devolver tambien las inactivas, anadir un campo- se filtre a lo que ve
 * cualquiera sin iniciar sesion.
 */
@Service
@RequiredArgsConstructor
public class MapaDeClinicasService {

    private final ClinicaRepository clinicas;

    /**
     * Las clinicas ACTIVAS de la red, opcionalmente de un solo departamento.
     *
     * Las INACTIVAS no salen nunca: una sede dada de baja no atiende, y
     * mostrarsela a un paciente que busca donde atenderse lo manda a una puerta
     * cerrada.
     *
     * Las que no tienen coordenadas SI salen. No se pueden dibujar, pero
     * existen y atienden; es la pantalla la que decide listarlas aparte en vez
     * de inventarles un punto. Ocultarlas aqui las haria desaparecer de la red
     * solo porque nadie ha ido aun a tomarles el GPS.
     *
     * Un departamento en blanco se trata como "sin filtro", que es lo que un
     * selector con la opcion "Todos" manda de forma natural.
     */
    @Transactional(readOnly = true)
    public List<ClinicaPublicaDto> listarActivas(String departamento) {
        List<Clinicas> activas = departamento == null || departamento.isBlank()
                ? clinicas.findByEstadoOrderByNameAsc(Clinicas.ESTADO_ACTIVA)
                : clinicas.activasDelDepartamento(departamento.trim());

        return activas.stream().map(MapaDeClinicasService::aPublica).toList();
    }

    // Se copia campo a campo, sin pasar por ClinicaMapper: lo que se publica
    // se decide aqui, no lo hereda del DTO del portal. El dueño (clinica.user)
    // ni se toca, asi que ademas no se dispara su carga perezosa.
    private static ClinicaPublicaDto aPublica(Clinicas c) {
        return new ClinicaPublicaDto(
                c.getClinicaId(),
                c.getName(),
                c.getLatitud(),
                c.getLongitud(),
                c.getDepartamento(),
                c.getMunicipio(),
                c.getDireccion(),
                c.getTelefono(),
                c.getHorario()
        );
    }
}
