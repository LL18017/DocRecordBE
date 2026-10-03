package ues.edu.sv.education;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.enums.RolesEnum;
import ues.edu.sv.education.repository.PersonaRepository;
import ues.edu.sv.education.repository.RoleRepository;
import ues.edu.sv.education.repository.UserRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Apoyo de las pruebas que necesitan un servidor HTTP DE VERDAD y no MockMvc.
 *
 * Hace falta para una sola cosa, pero no hay forma de rodearla: la IP real del
 * cliente detras de un proxy la resuelve la RemoteIpValve de Tomcat, que es
 * quien atiende server.forward-headers-strategy=native. MockMvc no levanta
 * Tomcat -- fabrica un MockHttpServletRequest y lo mete en la cadena de
 * filtros -- asi que una prueba con MockMvc pasaria exactamente igual con la
 * propiedad puesta y sin ella. Es decir, no probaria nada de lo que se quiere
 * probar.
 *
 * Se usa el cliente HTTP del JDK y no TestRestTemplate porque en Spring Boot 4
 * este ultimo se mudo a un modulo aparte (spring-boot-resttestclient); el del
 * JDK no depende de nada y aqui basta con mandar un POST con cabeceras.
 *
 * Las subclases declaran su propio @SpringBootTest con webEnvironment
 * RANDOM_PORT. Puerto aleatorio a proposito: en el entorno de desarrollo hay una
 * instancia del proyecto ocupando el 8080.
 */
abstract class PruebaConServidorReal extends PruebaDeIntegracion {

    @LocalServerPort protected int puerto;

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserRepository usuarios;
    @Autowired protected PersonaRepository personas;
    @Autowired protected RoleRepository roles;
    @Autowired protected PasswordEncoder encoder;

    protected static final String CLAVE = "Docrecord2026!";

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    /**
     * Crea un usuario habilitado y devuelve su correo.
     *
     * Se fabrica por repositorio en vez de por /auth/register porque el registro
     * deja la cuenta deshabilitada a la espera del correo de confirmacion, y sin
     * habilitarla el login no llega a generar el evento que aqui se inspecciona.
     */
    protected String crearMedicoHabilitado() {
        String correo = "ip.medico." + CONTADOR.incrementAndGet() + "@ues.edu.sv";

        Persona persona = personas.saveAndFlush(Persona.builder()
                .nombres("Medico").apellidos("De La Ip")
                .build());

        usuarios.saveAndFlush(User.builder()
                .persona(persona)
                .email(correo)
                .password(encoder.encode(CLAVE))
                .enabled(true)
                .roles(new HashSet<>(Set.of(roles.getReferenceById(RolesEnum.MEDICO.getId()))))
                .build());

        return correo;
    }

    /**
     * Inicia sesion por HTTP real. Las cabeceras extra van en pares
     * nombre/valor, que es como se simula el salto por un proxy inverso.
     */
    protected HttpResponse<String> iniciarSesion(String correo, String... cabecerasExtra) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + puerto + "/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"email":"%s","password":"%s"}
                        """.formatted(correo, CLAVE)));

        for (int i = 0; i < cabecerasExtra.length; i += 2) {
            peticion.header(cabecerasExtra[i], cabecerasExtra[i + 1]);
        }

        try (HttpClient cliente = HttpClient.newHttpClient()) {
            return cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    /**
     * IP guardada en el evento de inicio de sesion de ese correo.
     *
     * Se lee por SQL y no por el repositorio para mirar la COLUMNA, que es lo
     * que despues lee el trabajo programado que manda el aviso.
     */
    protected String ipDelEventoDeLogin(String correo) {
        return jdbc.queryForObject(
                "SELECT ip_address FROM public.events WHERE user_email = ? "
                        + "ORDER BY event_id DESC LIMIT 1",
                String.class, correo);
    }
}
