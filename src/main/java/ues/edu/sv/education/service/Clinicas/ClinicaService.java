package ues.edu.sv.education.service.Clinicas;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.clinicas.ClinicasRequestDto;
import ues.edu.sv.education.model.dto.clinicas.ClinicasResponseDto;
import ues.edu.sv.education.model.entity.Clinicas;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.repository.ClinicaRepository;
import ues.edu.sv.education.repository.UserRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClinicaService {
    private final UserRepository userRepository;
    private final ClinicaRepository clinicasRepository;

    /**
     * Las clinicas en las que este usuario puede operar hoy.
     *
     * Son DOS conjuntos y hacen falta los dos:
     *   · Las que registro (clinicas.user_id), que es lo unico que devolvia
     *     antes este metodo.
     *   · Aquellas a las que se le asigno como personal (clinica_personal,
     *     ver V10).
     *
     * Con solo el primero, enfermeria quedaba fuera del sistema entero: una
     * enfermera no da de alta sedes, trabaja en la que registro un medico, asi
     * que su lista salia vacia y la pantalla de seleccion de clinica la dejaba
     * encallada antes de poder hacer nada.
     *
     * Se unen sin repetir porque los dos conjuntos pueden solaparse -a alguien
     * se le puede asignar una sede que ademas registro-, y una clinica
     * duplicada en el selector es un error visible.
     */
    @Transactional(readOnly = true)
    public List<ClinicasResponseDto> obtenerPorUsuarioActual() {

        User user = usuarioActual();

        Map<Integer, Clinicas> porId = new LinkedHashMap<>();
        for (Clinicas propia : clinicasRepository.findByUser(user.getUserID())) {
            porId.put(propia.getClinicaId(), propia);
        }
        if (user.getClinicasAsignadas() != null) {
            for (Clinicas asignada : user.getClinicasAsignadas()) {
                porId.putIfAbsent(asignada.getClinicaId(), asignada);
            }
        }

        return porId.values().stream().map(this::toResponseDto).toList();
    }

    /**
     * El catalogo completo de clinicas, sin filtrar por dueño.
     *
     * Lo usa quien asigna personal a una sede: para poder asignar hay que ver
     * las sedes ajenas, y eso es exactamente lo que obtenerPorUsuarioActual no
     * devuelve. Por eso el endpoint que lo expone es solo para ADMIN.
     */
    @Transactional(readOnly = true)
    public List<ClinicasResponseDto> listarTodas() {
        return clinicasRepository.findAll().stream().map(this::toResponseDto).toList();
    }

    // Las coordenadas pueden llegar null: la columna las admite y una clinica
    // se da de alta con su nombre mucho antes de que alguien le tome el GPS.
    @Transactional
    public ClinicasResponseDto crear(ClinicasRequestDto dto) {

        User user = usuarioActual();

        Clinicas clinica = Clinicas.builder()
                .name(dto.name())
                .latitud(dto.latitud())
                .longitud(dto.longitud())
                .user(user)
                .build();

        clinicasRepository.save(clinica);

        return toResponseDto(clinica);
    }

    /**
     * Modifica una clinica existente.
     *
     * Sigue la misma regla que PacienteService.actualizar: completar nunca
     * destruye. Una coordenada que llega null significa "no la estoy tocando",
     * no "borrala". Sin esto, un formulario que solo corrige el nombre de la
     * clinica dejaria su ubicacion en blanco, y la ubicacion se pierde justo
     * cuando ya costo salir a tomarla.
     *
     * Para borrar unas coordenadas ya guardadas no basta con omitirlas: hace
     * falta un endpoint explicito, porque borrar debe ser algo que se pide, no
     * algo que pasa por descuido.
     */
    @Transactional
    public ClinicasResponseDto editar(
            Integer clinicaId,
            ClinicasRequestDto dto
    ) {

        Clinicas clinica = clinicasRepository.findById(clinicaId)
                .orElseThrow(() ->
                        new NoResourceFoundException("Clínica no encontrada", "404")
                );

        exigirPropietarioOAdmin(clinica);

        // El nombre es @NotBlank en el DTO, asi que siempre llega con valor.
        clinica.setName(dto.name());

        if (dto.latitud() != null) clinica.setLatitud(dto.latitud());
        if (dto.longitud() != null) clinica.setLongitud(dto.longitud());

        return toResponseDto(clinicasRepository.save(clinica));
    }

    @Transactional
    public void eliminar(Integer clinicaId) {

        Clinicas clinica = clinicasRepository.findById(clinicaId)
                .orElseThrow(() ->
                        new NoResourceFoundException("Clínica no encontrada", "404")
                );

        exigirPropietarioOAdmin(clinica);

        clinicasRepository.delete(clinica);
    }

    // ADMIN administra cualquier clinica; MEDICO solo las suyas. Que el
    // usuario tenga rol ADMIN o MEDICO ya lo exige @PreAuthorize en el
    // controller -- esto solo decide de QUIEN es la clinica.
    private void exigirPropietarioOAdmin(Clinicas clinica) {

        User actual = usuarioActual();

        boolean esAdmin = actual.getRoles().stream()
                .anyMatch(rol -> "ADMIN".equals(rol.getName()));

        if (!esAdmin && !clinica.getUser().getUserID().equals(actual.getUserID())) {
            throw new GeneralException(
                    "El usuario no tiene permiso para modificar esta clínica", "403"
            );
        }
    }

    // La identidad sale del JWT ya verificado (JwtFilter deja el user_id como
    // name() del Authentication), nunca del cuerpo ni de la ruta de la
    // petición -- de lo contrario cualquier usuario autenticado podria operar
    // clinicas de otro con solo cambiar un id en el request.
    private User usuarioActual() {

        String userId = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        return userRepository.findById(Integer.valueOf(userId))
                .orElseThrow(() ->
                        new NoResourceFoundException("Usuario no encontrado", "404")
                );
    }

    private ClinicasResponseDto toResponseDto(Clinicas clinica) {

        return new ClinicasResponseDto(
                clinica.getClinicaId(),
                clinica.getName(),
                clinica.getLatitud(),
                clinica.getLongitud()
        );
    }
}
