package ues.edu.sv.education;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ues.edu.sv.education.config.AdminBootstrap;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.enums.RolesEnum;
import ues.edu.sv.education.repository.PersonaRepository;
import ues.edu.sv.education.repository.RoleRepository;
import ues.edu.sv.education.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminBootstrap: el primer administrador se crea por variable de entorno al
 * arrancar, nunca por una migracion de Flyway (el hash quedaria escrito en un
 * archivo versionado, publico para siempre en GitHub).
 *
 * ── Por que este contexto es propio ───────────────────────────────────────
 * @DynamicPropertySource fija app.admin.email/app.admin.password a un valor
 * DISTINTO del resto de la suite, asi que Spring arma un ApplicationContext
 * dedicado para esta clase y el bean AdminBootstrap de ESE contexto queda
 * conectado con estas credenciales (prueba tambien que ADMIN_EMAIL/
 * ADMIN_PASSWORD llegan de verdad hasta el componente por
 * application.properties, no solo la logica en aislamiento).
 *
 * ── Por que se limpia y se invoca el bean a mano en cada prueba ───────────
 * La base de pruebas se recrea UNA sola vez para toda la corrida (ver
 * PruebaDeIntegracion), y la comparten muchas clases; varias de ellas
 * (PruebaClinica.tokenDeAdminQueNoEjerce) fabrican sus propios usuarios con
 * rol ADMIN para sus propios casos. Si esta clase solo mirara "existe algun
 * ADMIN" al arrancar el contexto, el resultado dependeria de en que orden
 * corriera Surefire frente a esas otras clases -- exactamente lo que
 * AdminBootstrap.run() debe respetar en produccion (no crear un segundo
 * admin), pero que aqui haria la prueba fragil. Por eso cada prueba limpia
 * los administradores previos e invoca adminBootstrap.run() ella misma: el
 * estado de partida queda bajo su control y el resultado no depende de que
 * mas corrio antes en la misma base.
 */
@AutoConfigureMockMvc
class AdminBootstrapIT extends PruebaDeIntegracion {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository usuarios;
    @Autowired private RoleRepository roles;
    @Autowired private PersonaRepository personas;
    @Autowired private PasswordEncoder encoder;
    @Autowired private AdminBootstrap adminBootstrap;

    private final ObjectMapper json = new ObjectMapper();

    private static final String ADMIN_EMAIL = "admin.arranque.it@ues.edu.sv";
    private static final String ADMIN_PASSWORD = "AdminArranque2026!";

    @DynamicPropertySource
    static void variablesDelAdmin(DynamicPropertyRegistry registro) {
        registro.add("app.admin.email", () -> ADMIN_EMAIL);
        registro.add("app.admin.password", () -> ADMIN_PASSWORD);
    }

    /**
     * Borra cualquier administrador que otra clase de la suite haya dejado en
     * la base compartida, para que cada prueba parta de un estado que ella
     * misma controla.
     */
    @BeforeEach
    void limpiarAdministradoresPrevios() {
        usuarios.deleteAll(usuarios.findByRolesName(RolesEnum.ADMIN.getName()));
    }

    @Test
    @DisplayName("con ADMIN_EMAIL/ADMIN_PASSWORD definidas y sin admin previo, se crea uno y puede iniciar sesion")
    void creaElAdminYPuedeIniciarSesionDeVerdad() throws Exception {
        adminBootstrap.run();

        User creado = usuarios.findByEmailContainingIgnoreCase(ADMIN_EMAIL)
                .orElseThrow(() -> new AssertionError("AdminBootstrap no creo el usuario administrador"));

        assertTrue(creado.isEnabled(), "el admin debe nacer habilitado; sin eso no sirve para nada");
        assertTrue(creado.getRoles().stream().anyMatch(r -> RolesEnum.ADMIN.getName().equals(r.getName())),
                "el usuario creado debe tener el rol ADMIN");

        // La unica forma real de comprobar que la contrasena quedo cifrada
        // con el encoder correcto (Argon2, el mismo que usa /auth/register):
        // iniciar sesion de verdad por HTTP con la contrasena EN TEXTO PLANO
        // y que el AuthenticationProvider real la valide contra el hash
        // guardado. Comprobar solo que la columna `password` no esta vacia no
        // demuestra nada sobre COMO quedo cifrada.
        String cuerpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(ADMIN_EMAIL, ADMIN_PASSWORD)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();

        String token = json.readTree(cuerpo).get("token").asText();
        assertNotNull(token);
        assertFalse(token.isBlank(), "el login del admin recien creado debe devolver un token valido");
    }

    @Test
    @DisplayName("correr el arranque dos veces con las mismas variables no crea un segundo admin ni cambia su contrasena")
    void arrancarDosVecesEsIdempotente() {
        adminBootstrap.run();

        User primero = usuarios.findByEmailContainingIgnoreCase(ADMIN_EMAIL)
                .orElseThrow(() -> new AssertionError("la primera corrida debio crear el admin"));
        Integer idOriginal = primero.getUserID();
        String hashOriginal = primero.getPassword();
        int totalAntes = usuarios.findByRolesName(RolesEnum.ADMIN.getName()).size();

        // Segunda corrida, mismas variables: ya existe un ADMIN (el que se
        // acaba de crear), asi que debe quedarse quieta.
        adminBootstrap.run();

        User segundo = usuarios.findByEmailContainingIgnoreCase(ADMIN_EMAIL)
                .orElseThrow(() -> new AssertionError("el admin desaparecio tras la segunda corrida"));
        int totalDespues = usuarios.findByRolesName(RolesEnum.ADMIN.getName()).size();

        assertEquals(idOriginal, segundo.getUserID(), "debe seguir siendo el mismo usuario, no uno nuevo");
        assertEquals(hashOriginal, segundo.getPassword(),
                "la contrasena no debe volver a cifrarse ni cambiar en una segunda corrida");
        assertEquals(totalAntes, totalDespues, "no debe aparecer un segundo administrador");

        // La misma garantia, pero con un correo DISTINTO al del admin que ya
        // existe (p.ej. el operador cambio ADMIN_EMAIL y volvio a desplegar).
        // Con el mismo correo, la comprobacion de "ya existe un ADMIN" (b) y
        // la de "el correo ya tiene cuenta" (d) protegen por caminos
        // distintos y una podria enmascarar a la otra; aqui el correo es
        // nuevo, asi que (d) no puede intervenir -- si esto no crea un
        // segundo administrador es exclusivamente porque (b) funciona.
        String otroCorreo = "otro." + ADMIN_EMAIL;
        AdminBootstrap conOtroCorreo =
                new AdminBootstrap(usuarios, roles, personas, encoder, otroCorreo, ADMIN_PASSWORD);
        conOtroCorreo.run();

        assertEquals(totalAntes, usuarios.findByRolesName(RolesEnum.ADMIN.getName()).size(),
                "ya existe un ADMIN (con otro correo); no debe crearse uno nuevo aunque ADMIN_EMAIL cambie");
        assertTrue(usuarios.findByEmailContainingIgnoreCase(otroCorreo).isEmpty(),
                "no debio registrarse ninguna cuenta con el correo nuevo");
    }

    @Test
    @DisplayName("sin ADMIN_EMAIL/ADMIN_PASSWORD no se crea ningun administrador")
    void sinLasVariablesNoHaceNada() {
        int totalAntes = usuarios.findByRolesName(RolesEnum.ADMIN.getName()).size();

        // Instancia propia con las dos variables vacias, tal como quedarian
        // si ADMIN_EMAIL/ADMIN_PASSWORD no se definen (application.properties
        // las resuelve a "" con ${ADMIN_EMAIL:} y ${ADMIN_PASSWORD:}, nunca a
        // null). Reutiliza los repositorios y el encoder reales del contexto;
        // lo unico que cambia es la config que normalmente pondria @Value.
        AdminBootstrap sinConfigurar = new AdminBootstrap(usuarios, roles, personas, encoder, "", "");
        sinConfigurar.run();

        int totalDespues = usuarios.findByRolesName(RolesEnum.ADMIN.getName()).size();
        assertEquals(totalAntes, totalDespues, "sin las variables no debe crearse ningun administrador");
        assertTrue(usuarios.findByEmailContainingIgnoreCase(ADMIN_EMAIL).isEmpty(),
                "sin las variables, el correo de prueba no debe haber quedado registrado");
    }
}
