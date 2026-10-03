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
import ues.edu.sv.education.model.mappers.ClinicaMapper;
import ues.edu.sv.education.model.enums.RolesEnum;
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

        // Un administrador opera sobre TODAS las sedes, sin que nadie tenga que
        // asignarselas una por una.
        //
        // Los dos conjuntos de abajo describen a quien TRABAJA en una sede, y
        // un administrador no trabaja en ninguna: administra el sistema entero.
        // Con la regla general quedaba encallado en la misma pantalla que
        // bloqueaba a enfermeria antes de V10 -- "todavia no tienes clinicas
        // registradas" -- y el unico modo de salir era darse de alta a si mismo
        // como personal de cada sede, que es papeleo que no describe nada real.
        //
        // Es la misma potestad que ya ejerce exigirPropietarioOAdmin al dejarle
        // editar y borrar cualquier clinica: seria incoherente que pudiera
        // modificar una sede que el selector no le deja ni ver.
        if (esAdministrador(user)) {
            return listarTodas();
        }

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

    /*
     * ── Por que las inactivas SI salen en este listado ────────────────────
     * Porque este endpoint sirve a dos pantallas: el selector de sede y la
     * administracion de clinicas. Filtrarlas aqui arreglaria la primera y
     * romperia la segunda -- una clinica dada de baja desapareceria del
     * catalogo y ya no habria desde donde reactivarla.
     *
     * Cada respuesta trae su `estado`, y es el selector el que descarta las
     * INACTIVA: es la pantalla que pregunta "donde puedo trabajar HOY", y la
     * unica que necesita esa distincion.
     */

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

        exigirQueNoExistaEnElMunicipio(dto.name(), dto.municipio(), null);

        Clinicas clinica = Clinicas.builder()
                .name(dto.name())
                .latitud(dto.latitud())
                .longitud(dto.longitud())
                .departamento(dto.departamento())
                .municipio(dto.municipio())
                .direccion(dto.direccion())
                .telefono(dto.telefono())
                .horario(dto.horario())
                .estado(Clinicas.ESTADO_ACTIVA)
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

        // Se comprueba con el nombre y el municipio QUE VAN A QUEDAR, no con los
        // que tenia: si se renombra a uno que ya existe en ese municipio, el
        // choque es el mismo que al crearla. Se excluye a si misma, o guardarla
        // sin cambiarle el nombre chocaria consigo.
        exigirQueNoExistaEnElMunicipio(dto.name(), dto.municipio(), clinicaId);

        // El nombre es @NotBlank en el DTO, asi que siempre llega con valor.
        clinica.setName(dto.name());

        if (dto.latitud() != null) clinica.setLatitud(dto.latitud());
        if (dto.longitud() != null) clinica.setLongitud(dto.longitud());
        if (dto.departamento() != null) clinica.setDepartamento(dto.departamento());
        if (dto.municipio() != null) clinica.setMunicipio(dto.municipio());
        if (dto.direccion() != null) clinica.setDireccion(dto.direccion());
        if (dto.telefono() != null) clinica.setTelefono(dto.telefono());
        if (dto.horario() != null) clinica.setHorario(dto.horario());

        return toResponseDto(clinicasRepository.save(clinica));
    }

    /**
     * Da de baja una clinica. La baja es LOGICA: marca INACTIVA, no borra.
     *
     * ── Por que no se borra la fila ───────────────────────────────────────
     * Antes esto era `clinicasRepository.delete(...)`. Una clinica no es un
     * dato suelto: es la sede que firma cada consulta, cada receta y cada toma
     * de constantes. Borrarla deja ese historial apuntando a una sede que ya no
     * existe -- o lo arrastra en cascada, que es peor, porque se lleva actos
     * medicos que si ocurrieron.
     *
     * HU-27 pide "edicion o baja para mantener vigente la informacion de las
     * sedes". Vigente es lo contrario de borrado: una sede cerrada sigue siendo
     * la sede donde se atendio a alguien en su momento.
     *
     * Es idempotente: dar de baja a la que ya esta de baja la deja igual.
     */
    @Transactional
    public void eliminar(Integer clinicaId) {

        Clinicas clinica = clinicasRepository.findById(clinicaId)
                .orElseThrow(() ->
                        new NoResourceFoundException("Clínica no encontrada", "404")
                );

        exigirPropietarioOAdmin(clinica);

        clinica.setEstado(Clinicas.ESTADO_INACTIVA);
        clinicasRepository.save(clinica);
    }

    // ADMIN administra cualquier clinica; MEDICO solo las suyas. Que el
    // usuario tenga rol ADMIN o MEDICO ya lo exige @PreAuthorize en el
    // controller -- esto solo decide de QUIEN es la clinica.
    private void exigirPropietarioOAdmin(Clinicas clinica) {

        User actual = usuarioActual();

        if (!esAdministrador(actual) && !clinica.getUser().getUserID().equals(actual.getUserID())) {
            throw new GeneralException(
                    "El usuario no tiene permiso para modificar esta clínica", "403"
            );
        }
    }

    // Se compara sin distinguir mayusculas y tolerando roles nulos: la version
    // anterior, "ADMIN".equals(rol.getName()) escrita a mano aqui dentro, daba
    // false ante un "Administrador" guardado con otra caja y dejaba a un
    // administrador real sin sus permisos, sin ningun error que lo delatara.
    private boolean esAdministrador(User user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(rol -> RolesEnum.ADMIN.getName().equalsIgnoreCase(rol.getName()));
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

    // Delega en el unico mapper que arma este DTO. Antes lo armaba aqui y
    // ademas en UserService.clinicasAsignadas, y al anadirle campos a la
    // clinica el segundo dejo de compilar. Ver ClinicaMapper.
    private ClinicasResponseDto toResponseDto(Clinicas clinica) {
        return ClinicaMapper.toDto(clinica);
    }

    /**
     * Impide dos clinicas con el mismo nombre en el mismo municipio (HU-26
     * criterio 4).
     *
     * Solo comprueba cuando hay municipio. Las clinicas registradas antes de
     * V16 no lo tienen, y sin municipio no se puede decidir si dos chocan:
     * inventar el conflicto seria peor que dejarlo pasar.
     *
     * El indice unico de V16 hace la misma guarda en la base. Esta existe
     * ademas para que el error salga como un 409 que explica el choque, y no
     * como un fallo de integridad convertido en 500.
     */
    private void exigirQueNoExistaEnElMunicipio(String nombre, String municipio, Integer excluirId) {
        if (municipio == null || municipio.isBlank()) return;

        if (clinicasRepository.existeEnElMunicipio(nombre, municipio, excluirId)) {
            throw new GeneralException(
                    "Ya existe una clinica con ese nombre en " + municipio, "409");
        }
    }

    /**
     * Da de alta o de baja una clinica (HU-27).
     *
     * No borra: las consultas que se atendieron ahi ocurrieron ahi, y el
     * personal asignado sigue asignado. Una clinica inactiva deja de ofrecerse
     * para atender, nada mas.
     */
    @Transactional
    public ClinicasResponseDto cambiarEstado(Integer clinicaId, String estado) {
        String limpio = estado == null ? "" : estado.trim().toUpperCase();

        if (!Clinicas.ESTADO_ACTIVA.equals(limpio) && !Clinicas.ESTADO_INACTIVA.equals(limpio)) {
            throw new GeneralException("Estado no valido: se espera ACTIVA o INACTIVA", "400");
        }

        Clinicas clinica = clinicasRepository.findById(clinicaId)
                .orElseThrow(() -> new NoResourceFoundException("Clínica no encontrada", "404"));

        exigirPropietarioOAdmin(clinica);

        clinica.setEstado(limpio);
        return toResponseDto(clinicasRepository.save(clinica));
    }
}
