package ues.edu.sv.education.service.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ues.edu.sv.education.controller.error.GeneralException;
import ues.edu.sv.education.controller.error.NoResourceFoundException;
import ues.edu.sv.education.model.dto.User.AltaUsuarioResponseDto;
import ues.edu.sv.education.model.dto.clinicas.ClinicasResponseDto;
import ues.edu.sv.education.model.dto.User.UserRequestDto;
import ues.edu.sv.education.model.dto.User.UserResponseDto;
import ues.edu.sv.education.model.entity.Clinicas;
import ues.edu.sv.education.model.entity.Enfermera;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.Role;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.entity.VerificationToken;
import ues.edu.sv.education.model.enums.RolesEnum;
import ues.edu.sv.education.model.mappers.UserMapper;
import ues.edu.sv.education.repository.ClinicaRepository;
import ues.edu.sv.education.repository.EnfermeraRepository;
import ues.edu.sv.education.repository.PersonaRepository;
import ues.edu.sv.education.repository.RoleRepository;
import ues.edu.sv.education.repository.UserRepository;
import ues.edu.sv.education.repository.VerificationTokenRepository;
import ues.edu.sv.education.service.EmailService;
import ues.edu.sv.education.service.PlantillaDeCorreo;
import ues.edu.sv.education.service.auth.AdminAutenticado;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PersonaRepository personaRepository;
    private final RoleRepository roleRepository;
    private final ClinicaRepository clinicaRepository;
    private final EnfermeraRepository enfermeraRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;
    private final AdminAutenticado adminAutenticado;
    // El mismo bean que usa /auth/register: Argon2PasswordEncoder, definido en
    // Security/BasicConfiguration.passwordEncoder().
    private final PasswordEncoder passwordEncoder;
    private static final long TOKEN_EXPIRATION_MINUTES = 5;

    public List<UserResponseDto> getAll(Integer inicio, Integer fin) {
        Pageable pageable = PageRequest.of(inicio, fin);
        return userRepository.findAll(pageable).stream().map(UserMapper::toDto).toList();
    }

    // @Transactional porque ahora hace DOS escrituras que tienen que ir juntas:
    // el rol y, para ENFERMERA, su ficha. Media asignacion -rol sin ficha- es
    // justo el estado que deja a alguien pasando el hasRole y recibiendo 403
    // del servicio, sin forma de entender por que.
    @Transactional
    public UserResponseDto addRole(int userID, int roleID) {
        Optional<User> userOpt = userRepository.findById(userID);
        if (userOpt.isEmpty()) {
            throw new NoResourceFoundException("No se encontro el usuario con id: " + userID, "404");
        }
        Optional<Role> roleOpt = roleRepository.findById(roleID);
        if (roleOpt.isEmpty()) {
            throw new NoResourceFoundException("No se encontro el rol con id: " + roleID, "404");
        }
        User user = userOpt.get();
        Role role = roleOpt.get();
        if (user.getRoles().contains(role)) {
            throw new GeneralException("El usuario ya cuenta con este rol", "409");
        }
        user.getRoles().add(role);
        userRepository.save(user);

        // Asignar el rol ENFERMERA crea tambien su ficha en `enfermeras`.
        //
        // Sin esto la asignacion queda a medias de la peor forma posible: la
        // cuenta pasa el hasRole('ENFERMERA') del controlador de signos
        // vitales y despues el servicio la rechaza con 403, porque lo que
        // autoriza a tomar constantes es la FILA, no la etiqueta del token.
        // Quien lo sufre no tiene como saber que le falta.
        //
        // Con MEDICO no se puede hacer lo mismo: `medicos.especialidad_id` es
        // NOT NULL y nadie puede adivinar la especialidad. Esa alta sigue
        // pasando por /auth/register, que si la pide.
        if (RolesEnum.ENFERMERA.getId() == roleID) {
            asegurarFichaDeEnfermeria(user);
        }

        return UserMapper.toDto(user);
    }

    /**
     * Quita un rol.
     *
     * ── Las dos guardas, y por que ────────────────────────────────────────
     * Nadie puede quitarse el ADMIN a si mismo, y no se puede quitar el ultimo
     * ADMIN que queda. Las dos protegen del mismo accidente: dejar el sistema
     * sin nadie que pueda administrarlo. No hay forma de recuperarse de eso
     * desde la aplicacion -AdminBootstrap solo actua si NO existe ningun admin
     * y ademas se niega a promover cuentas que ya existen-, asi que habria que
     * arreglarlo a mano en la base.
     *
     * Al quitar ENFERMERA la fila de `enfermeras` se marca inactiva, NO se
     * borra: sus tomas de signos vitales la referencian (FK con ON DELETE
     * RESTRICT en V9) y borrarla dejaria constantes sin responsable. Una
     * correccion de permisos no puede reescribir el historial clinico.
     */
    @Transactional
    public UserResponseDto quitarRole(int userID, int roleID) {

        User user = userRepository.findById(userID)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontro el usuario con id: " + userID, "404"));

        Role role = roleRepository.findById(roleID)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontro el rol con id: " + roleID, "404"));

        if (!user.getRoles().contains(role)) {
            throw new NoResourceFoundException("El usuario no tiene ese rol", "404");
        }

        // Las guardas van ANTES de tocar la coleccion, y no es un detalle de
        // estilo. Quitando primero, `adminAutenticado.exigir()` vuelve a leer
        // al usuario DENTRO de la misma transaccion y lo encuentra ya sin el
        // rol: un administrador que se quita su propio ADMIN recibia un 403
        // diciendole que no es administrador, en vez del 409 que explica la
        // regla. Se comprobo ejecutandolo.
        if (RolesEnum.ADMIN.getId() == roleID) {
            User quienOpera = adminAutenticado.exigir();
            if (quienOpera.getUserID().equals(user.getUserID())) {
                throw new GeneralException(
                        "No puede quitarse a si mismo el rol de administrador", "409");
            }
            if (userRepository.findByRolesName(RolesEnum.ADMIN.getName()).size() <= 1) {
                throw new GeneralException(
                        "No se puede quitar el ultimo administrador del sistema", "409");
            }
        }

        user.getRoles().remove(role);
        userRepository.save(user);

        if (RolesEnum.ENFERMERA.getId() == roleID) {
            enfermeraRepository.findById(user.getPersona().getPersonaId())
                    .ifPresent(enfermera -> {
                        enfermera.setActivo(false);
                        enfermeraRepository.save(enfermera);
                    });
        }

        return UserMapper.toDto(user);
    }

    /**
     * Deja lista la ficha de enfermeria de esta persona.
     *
     * Si ya existe pero estaba dada de baja, se reactiva en vez de fallar:
     * volver a asignarle el rol a alguien que volvio es el caso normal, y un
     * error ahi obligaria a tocar la base a mano.
     */
    private void asegurarFichaDeEnfermeria(User user) {
        Long personaId = user.getPersona().getPersonaId();
        enfermeraRepository.findById(personaId)
                .ifPresentOrElse(
                        enfermera -> {
                            if (!enfermera.isActivo()) {
                                enfermera.setActivo(true);
                                enfermeraRepository.save(enfermera);
                            }
                        },
                        () -> enfermeraRepository.save(Enfermera.builder()
                                .persona(user.getPersona())
                                .activo(true)
                                .build()));
    }

    /**
     * Le da acceso a una sede sin volverlo su dueño.
     *
     * ── Por que existe, y por que NO se toca clinicas.user_id ─────────────
     * Esa columna significa QUIEN REGISTRO la clinica, y es una sola. Moverla
     * para que una enfermera "tuviera" la sede se la quitaria al medico que la
     * dio de alta. Trabajar en un sitio y ser su dueño son cosas distintas, asi
     * que se guardan por separado (clinica_personal, ver V10).
     *
     * Sin esto el sistema solo sabia responder "que clinicas creaste tu", y
     * enfermeria -que no crea ninguna- quedaba con la lista vacia y sin poder
     * pasar de la pantalla de seleccion de clinica.
     */
    @Transactional
    public UserResponseDto asignarClinica(int userId, int clinicaId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontro el usuario con id: " + userId, "404"));

        Clinicas clinica = clinicaRepository.findById(clinicaId)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontro la clinica con id: " + clinicaId, "404"));

        // La coleccion puede venir null en una entidad recien construida por
        // el builder; el PK compuesto de clinica_personal ya impide duplicar,
        // pero el 409 explicito dice por que en vez de dejar salir un error de
        // restriccion.
        if (user.getClinicasAsignadas() == null) {
            user.setClinicasAsignadas(new HashSet<>());
        }
        if (user.getClinicasAsignadas().stream()
                .anyMatch(c -> Objects.equals(c.getClinicaId(), clinica.getClinicaId()))) {
            throw new GeneralException("El usuario ya tiene asignada esta clinica", "409");
        }

        user.getClinicasAsignadas().add(clinica);
        userRepository.save(user);

        return UserMapper.toDto(user);
    }

    /**
     * Las clinicas ASIGNADAS a un usuario (no las que registro).
     *
     * Endpoint propio en vez de un campo mas en UserResponseDto, y la razon es
     * concreta: `User.clinicasAsignadas` es LAZY, y UserMapper.toDto se llama
     * desde sitios que NO estan dentro de una transaccion -getAll, por
     * ejemplo-. Anadirla al DTO compartido haria reventar esas rutas con
     * LazyInitializationException, y volverla EAGER cargaria las clinicas en
     * cada carga de usuario, incluido el login.
     *
     * Aqui se lee dentro de una transaccion de solo lectura, que es donde la
     * coleccion se puede tocar sin riesgo, y solo lo paga quien la pide.
     */
    @Transactional(readOnly = true)
    public List<ClinicasResponseDto> clinicasAsignadas(int userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontro el usuario con id: " + userId, "404"));

        if (user.getClinicasAsignadas() == null) return List.of();

        return user.getClinicasAsignadas().stream()
                .sorted(Comparator.comparing(Clinicas::getClinicaId))
                .map(c -> new ClinicasResponseDto(
                        c.getClinicaId(), c.getName(), c.getLatitud(), c.getLongitud()))
                .toList();
    }

    /** Retira la asignacion. No borra la clinica ni afecta a su dueño. */
    @Transactional
    public UserResponseDto quitarClinica(int userId, int clinicaId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontro el usuario con id: " + userId, "404"));

        boolean estaba = user.getClinicasAsignadas() != null
                && user.getClinicasAsignadas()
                        .removeIf(c -> Objects.equals(c.getClinicaId(), clinicaId));

        if (!estaba) {
            throw new NoResourceFoundException(
                    "El usuario no tiene asignada la clinica con id: " + clinicaId, "404");
        }

        userRepository.save(user);

        return UserMapper.toDto(user);
    }

    /**
     * Alta directa de una cuenta por un administrador (POST /user).
     *
     * ── La contrasena se cifra ANTES de guardarla ─────────────────────────
     * Este metodo guardaba la contrasena tal como llegaba en el cuerpo de la
     * peticion: la columna users.password quedaba con el texto legible, y se
     * comprobo en la base de desarrollo leyendo "Docrecord2026!" sin cifrar.
     * Cualquiera con acceso de lectura a la tabla -un SELECT, un volcado, una
     * copia de seguridad mal guardada- se llevaba las credenciales de todo el
     * personal de un expediente clinico; y como la gente reusa contrasenas, el
     * dano no se habria quedado en este sistema. Ahora pasa por el mismo
     * PasswordEncoder (argon2) que /auth/register, que es el unico flujo que
     * lo hacia bien.
     *
     * ── Confirmacion por correo, igual que /auth/register ─────────────────
     * Nace con enabled = false -lo fija UserMapper.toEntity- y ahora si se le
     * emite un VerificationToken y se manda el correo de confirmacion, mismo
     * patron que AuthService.registrarMedico. Si el correo falla (MailException),
     * la cuenta se crea de todos modos -no depender de un SMTP externo para dar
     * de alta personal- y correoDeVerificacionEnviado sale en false para que el
     * cliente lo sepa. En ese caso el administrador tiene una salida aparte:
     * asignarContrasena tambien habilita la cuenta, sin depender de que el
     * correo llegue.
     */
    @Transactional
    public AltaUsuarioResponseDto createUser(UserRequestDto userRequest) {
        String[] nombreDividido = dividirNombreCompleto(userRequest.userName());
        Persona persona = personaRepository.save(
                Persona.builder()
                        .nombres(nombreDividido[0])
                        .apellidos(nombreDividido[1])
                        .build()
        );
        User user = UserMapper.toEntity(userRequest, persona,
                passwordEncoder.encode(userRequest.password()));
        User savedUser = userRepository.save(user);

        String token = UUID.randomUUID().toString();
        verificationTokenRepository.save(new VerificationToken(
                null, token, savedUser, LocalDateTime.now().plusMinutes(TOKEN_EXPIRATION_MINUTES), false));

        boolean correoEnviado = enviarCorreoDeVerificacion(
                savedUser.getEmail(), userRequest.userName(), token);

        UserResponseDto dto = UserMapper.toDto(savedUser);
        return new AltaUsuarioResponseDto(
                dto.userId(), dto.email(), dto.userName(), dto.roles(), correoEnviado);
    }

    // Mismo patron que AuthService.enviarCorreoDeVerificacion: solo se atrapa
    // MailException -un problema de SMTP no debe tumbar el alta de la cuenta-
    // y se deja constancia en el log de cualquier otra falla real.
    private boolean enviarCorreoDeVerificacion(String correoDestino, String nombre, String token) {
        String enlace = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/auth/confirm")
                .queryParam("token", token)
                .toUriString();
        Map<String, Object> datos = new HashMap<>();
        datos.put("nombre", nombre);
        datos.put("enlace", enlace);
        datos.put("minutos", TOKEN_EXPIRATION_MINUTES);
        try {
            emailService.enviarCorreo(correoDestino, PlantillaDeCorreo.CONFIRMACION_DE_REGISTRO, datos);
            return true;
        } catch (MailException e) {
            log.error("No se pudo enviar el correo de confirmacion a {}", correoDestino, e);
            return false;
        }
    }

    /**
     * Un administrador asigna una contrasena nueva a otro usuario (o a si
     * mismo) por POST /user/{userId}/password.
     *
     * ── Tambien habilita la cuenta ─────────────────────────────────────────
     * Si la cuenta nacio deshabilitada porque el correo de confirmacion nunca
     * salio (createUser con correoDeVerificacionEnviado = false), esta es la
     * salida: un administrador identificado asignando la contrasena a mano es
     * al menos tan fuerte como un enlace de correo, asi que no tiene sentido
     * dejar la cuenta atrapada sin acceso. Se borra ademas cualquier token de
     * verificacion pendiente -ya no aplica, la cuenta quedo habilitada aqui-.
     *
     * ── No se cierran sesiones existentes ──────────────────────────────────
     * JwtService no tiene mecanismo de revocacion/lista negra (ver DRS-79 /
     * DRS-8): los tokens ya emitidos son validos hasta que expiran por su
     * propio "exp", sin excepcion. Construir revocacion aqui -una lista negra,
     * un numero de version por usuario que JwtFilter tendria que consultar en
     * cada peticion autenticada- es infraestructura nueva que toca el camino
     * caliente de todo el sistema, desproporcionada para esta tarea. La
     * mitigacion real es que el access token dura poco (15 min).
     *
     * ── Un administrador no puede tocar la contrasena de OTRO administrador ─
     * Si pudiera, cualquier cuenta ADMIN comprometida se convierte en control
     * total sobre todas las demas cuentas ADMIN: no es conveniencia, es
     * escalacion de privilegios. Un administrador SI puede asignarse una
     * contrasena a si mismo (mismo criterio que "cambiar mi propia
     * contrasena", no un ataque a un tercero).
     */
    @Transactional
    public UserResponseDto asignarContrasena(int userId, String nuevaContrasena) {
        User administrador = adminAutenticado.exigir();

        User objetivo = userRepository.findById(userId)
                .orElseThrow(() -> new NoResourceFoundException(
                        "No se encontro el usuario con id: " + userId, "404"));

        boolean objetivoEsAdmin = objetivo.getRoles().stream()
                .anyMatch(rol -> RolesEnum.ADMIN.getName().equalsIgnoreCase(rol.getName()));
        if (objetivoEsAdmin && !objetivo.getUserID().equals(administrador.getUserID())) {
            throw new GeneralException(
                    "Un administrador no puede asignar la contrasena de otro administrador", "403");
        }

        objetivo.setPassword(passwordEncoder.encode(nuevaContrasena));
        objetivo.setEnabled(true);
        userRepository.save(objetivo);
        verificationTokenRepository.deleteAllByUser(objetivo);

        return UserMapper.toDto(objetivo);
    }

    public void deleteUser(String userEmail) {
        User user = userRepository.findByEmailContainingIgnoreCase(userEmail).orElseThrow(()-> new NoResourceFoundException("Usuario no encontrado","404"));
        userRepository.delete(user);
    }

    // Ver el mismo helper en AuthService: userName sigue siendo un solo campo
    // hasta que este endpoint tambien pida nombres/apellidos por separado.
    private String[] dividirNombreCompleto(String nombreCompleto) {
        String limpio = nombreCompleto.trim();
        int espacio = limpio.indexOf(' ');
        if (espacio < 0) {
            return new String[]{limpio, limpio};
        }
        String nombres = limpio.substring(0, espacio);
        String apellidos = limpio.substring(espacio + 1).trim();
        return new String[]{nombres, apellidos.isEmpty() ? nombres : apellidos};
    }
}
