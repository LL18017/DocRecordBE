package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import ues.edu.sv.education.model.entity.Enfermera;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.enums.RolesEnum;
import ues.edu.sv.education.repository.EnfermeraRepository;
import ues.edu.sv.education.repository.PersonaRepository;
import ues.edu.sv.education.repository.RoleRepository;
import ues.edu.sv.education.repository.UserRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Apoyo comun de las pruebas del dominio clinico (consultas y recetas).
 *
 * Existe porque estas pruebas necesitan algo que las anteriores no: usuarios de
 * roles DISTINTOS del de medico. Hasta ahora toda la suite se autenticaba con
 * un medico registrado por /auth/register, que es el unico alta que el sistema
 * expone; una enfermera o un administrador hay que fabricarlos a mano.
 *
 * Se fabrican por repositorio y despues se inicia sesion por HTTP, de modo que
 * el token es real y pasa por el JwtFilter igual que en produccion. Autenticar
 * con @WithMockUser probaria una cadena distinta de la que corre de verdad
 * (ver la nota de SeguridadIT).
 */
@AutoConfigureMockMvc
abstract class PruebaClinica extends PruebaDeIntegracion {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected UserRepository usuarios;
    @Autowired protected PersonaRepository personas;
    @Autowired protected RoleRepository roles;
    @Autowired protected EnfermeraRepository enfermeras;
    @Autowired protected PasswordEncoder encoder;

    protected final ObjectMapper json = new ObjectMapper();

    /**
     * Cada alta usa un correo distinto porque el registro COMMITEA: la clase no
     * es @Transactional a proposito, ya que MockMvc debe ver al usuario
     * guardado para poder autenticarlo.
     */
    private static final AtomicInteger CONTADOR = new AtomicInteger();

    protected static final String CLAVE = "Docrecord2026!";

    protected int siguiente() {
        return CONTADOR.incrementAndGet();
    }

    protected String correoUnico(String etiqueta) {
        return etiqueta + "." + siguiente() + "@ues.edu.sv";
    }

    protected String duiUnico() {
        return String.format("%08d-2", siguiente() + 20_000_000);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Usuarios
    // ══════════════════════════════════════════════════════════════════════

    /** Registra un medico por la API publica, lo habilita y devuelve su token. */
    protected String tokenDeMedico() throws Exception {
        String correo = correoUnico("clinico.medico");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Medico","apellidos":"Del Turno",
                                 "email":"%s","password":"%s","especialidadId":1}
                                """.formatted(correo, CLAVE)))
                .andExpect(status().is2xxSuccessful());

        User usuario = usuarios.findByEmailContainingIgnoreCase(correo)
                .orElseThrow(() -> new AssertionError("el registro no creo el usuario"));
        usuario.setEnabled(true);
        usuarios.saveAndFlush(usuario);

        return iniciarSesion(correo);
    }

    /**
     * Crea una enfermera y devuelve su token.
     *
     * Se le crea tambien su fila en `enfermeras`, no solo el rol: el rol dice
     * lo que el token afirma, la fila dice lo que el sistema sabe. Es la misma
     * distincion que hace MedicoAutenticado del otro lado.
     */
    protected String tokenDeEnfermera() throws Exception {
        String correo = correoUnico("clinico.enfermera");

        Persona persona = personas.saveAndFlush(Persona.builder()
                .nombres("Enfermera").apellidos("De Turno")
                .build());

        crearUsuario(persona, correo, RolesEnum.ENFERMERA);

        // Ojo: no fijar personaId a mano. Con @MapsId lo deriva de la persona
        // al persistir; con el id ya puesto, Spring Data haria merge en vez de
        // persist (ver el mismo comentario en AuthService).
        enfermeras.saveAndFlush(Enfermera.builder().persona(persona).activo(true).build());

        return iniciarSesion(correo);
    }

    /**
     * Crea un administrador que NO ejerce la medicina -- sin fila en `medicos`
     * -- y devuelve su token.
     *
     * Es el caso que separa "puede entrar al endpoint" de "puede firmar un acto
     * clinico", y sin el la regla del diagnostico no se podria probar de
     * verdad: una enfermera se queda fuera ya en la anotacion del controller,
     * mientras que este usuario la atraviesa y tiene que ser el servicio quien
     * lo detenga.
     */
    protected String tokenDeAdminQueNoEjerce() throws Exception {
        String correo = correoUnico("clinico.admin");

        Persona persona = personas.saveAndFlush(Persona.builder()
                .nombres("Admin").apellidos("De Sistema")
                .build());

        crearUsuario(persona, correo, RolesEnum.ADMIN);

        return iniciarSesion(correo);
    }

    private void crearUsuario(Persona persona, String correo, RolesEnum rol) {
        usuarios.saveAndFlush(User.builder()
                .persona(persona)
                .email(correo)
                .password(encoder.encode(CLAVE))
                .enabled(true)
                .roles(new HashSet<>(Set.of(roles.getReferenceById(rol.getId()))))
                .build());
    }

    private String iniciarSesion(String correo) throws Exception {
        String cuerpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(correo, CLAVE)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();

        return json.readTree(cuerpo).get("token").asText();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Datos clinicos
    // ══════════════════════════════════════════════════════════════════════

    /** Da de alta un paciente por la API y devuelve su persona_id. */
    protected long crearPaciente(String token) throws Exception {
        String cuerpo = mockMvc.perform(post("/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"persona":{"dui":"%s","nombres":"Paciente","apellidos":"Del Caso %d",
                                            "fechaNacimiento":"1990-05-20","sexo":"F"},
                                 "tipoSangre":"O+"}
                                """.formatted(duiUnico(), siguiente())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return json.readTree(cuerpo).get("personaId").asLong();
    }

    protected JsonNode crearConsulta(String token, String cuerpoJson) throws Exception {
        String respuesta = mockMvc.perform(post("/consultas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return json.readTree(respuesta);
    }

    /** Consulta minima: solo paciente y motivo, sin clinica ni diagnostico. */
    protected JsonNode crearConsultaSimple(String token, long pacienteId) throws Exception {
        return crearConsulta(token, """
                {"pacienteId":%d,"motivo":"Control general"}
                """.formatted(pacienteId));
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }
}
