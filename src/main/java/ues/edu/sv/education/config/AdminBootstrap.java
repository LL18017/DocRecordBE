package ues.edu.sv.education.config;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.Role;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.enums.RolesEnum;
import ues.edu.sv.education.repository.PersonaRepository;
import ues.edu.sv.education.repository.RoleRepository;
import ues.edu.sv.education.repository.UserRepository;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/*
 * Siembra el primer administrador AL ARRANCAR, no por una migracion de
 * Flyway ni por data.sql.
 *
 * El problema que resuelve: /user exige ROLE_ADMIN a nivel de clase, ninguna
 * migracion ni data.sql siembra un usuario o una fila de user_roles, y la
 * unica alta publica (/auth/register) solo crea cuentas MEDICO. Un despliegue
 * nuevo arranca sin nadie que pueda administrar el sistema.
 *
 * Por que variable de entorno y no una migracion SQL: una migracion dejaria
 * el hash de la contrasena escrito en un archivo versionado, que vive para
 * siempre en un repositorio publico de GitHub (el mismo motivo por el que la
 * clave de JWT y la contrasena del correo tuvieron que rotarse, ver
 * application.properties). Una variable de entorno no entra al historial de
 * git.
 *
 * Por que CommandLineRunner y no un BeanPostProcessor como FlywayBootstrap:
 * FlywayBootstrap usa un BeanPostProcessor por una razon puntual de ESE
 * problema -- necesita interceptar el propio DataSource antes de que
 * Hibernate lo use, porque este build de Boot no dispara Flyway solo. Aqui no
 * hay ninguna carrera que ganarle: al contrario, hace falta lo opuesto, que
 * el contexto ya este completamente listo (repositorios, PasswordEncoder,
 * y sobre todo que Flyway y Hibernate ya hayan corrido) antes de tocar la
 * base. CommandLineRunner es exactamente eso: Spring Boot lo ejecuta despues
 * de que el contexto termino de levantar.
 */
@Slf4j
@Component
public class AdminBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PersonaRepository personaRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    // Constructor explicito (no @RequiredArgsConstructor de Lombok) porque
    // ADMIN_EMAIL y ADMIN_PASSWORD se leen con @Value sobre un parametro, y
    // Lombok no genera esas anotaciones en el constructor que fabrica.
    public AdminBootstrap(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PersonaRepository personaRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.admin.email:}") String adminEmail,
                           @Value("${app.admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.personaRepository = personaRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // (a) Sin las dos variables no se hace absolutamente nada. Este es el
        // caso normal en desarrollo local: nadie deberia notar que esta clase
        // existe. No se escribe ni una linea de log, ni siquiera en debug,
        // para que arrancar sin las variables sea identico a como era antes
        // de que esta clase existiera.
        if (esVacia(adminEmail) || esVacia(adminPassword)) {
            return;
        }

        // (b) Ya existe un ADMIN: no se hace nada mas. Ni se pisa, ni se
        // duplica, ni se le cambia la contrasena a nadie. Arrancar la
        // aplicacion 10 veces con las mismas variables debe dejar exactamente
        // el mismo estado que arrancarla una vez.
        if (userRepository.existsByRolesName(RolesEnum.ADMIN.getName())) {
            return;
        }

        // (d) ADMIN_EMAIL coincide con una cuenta que YA EXISTE (por ejemplo,
        // alguien que se autoregistro como medico con ese correo antes de que
        // se configurara la variable).
        //
        // Se decide NO promoverla nunca automaticamente. El riesgo concreto es
        // que /auth/register es publico: si la respuesta fuera "se le agrega
        // el rol ADMIN", cualquiera que adivine o conozca de antemano cual
        // sera el ADMIN_EMAIL del despliegue podria autoregistrarse con ese
        // correo POR ADELANTADO, y quedar promovido a administrador con solo
        // esperar a que la aplicacion se reinicie -- sin haber demostrado
        // nunca que controla esa cuenta mas alla de haber llenado un
        // formulario. Promover en silencio convertiria la variable de entorno
        // (secreta) en una carrera contra un endpoint publico (no secreto).
        //
        // La alternativa seria pedir confirmacion explicita (otra variable,
        // p.ej. ADMIN_FORCE_PROMOTE=true) para ese caso puntual, pero eso
        // vuelve a poner una decision de seguridad detras de una bandera que
        // alguien puede dejar encendida por costumbre. No crear nada y dejar
        // constancia en el log -- para que un humano decida a mano, con
        // `UPDATE`/el endpoint de roles, una vez verificada la identidad de
        // quien tiene esa cuenta -- es la opcion que no se puede automatizar
        // para atacar.
        Optional<User> existente = userRepository.findByEmailContainingIgnoreCase(adminEmail);
        if (existente.isPresent()) {
            // Sin la contrasena, sin el hash: solo el correo, que ya es
            // publico (es el que el propio operador configuro en
            // ADMIN_EMAIL).
            log.warn("ADMIN_EMAIL ({}) ya pertenece a una cuenta existente sin rol ADMIN. "
                    + "No se crea ni se promueve automaticamente por seguridad "
                    + "(ver AdminBootstrap). Asignar el rol a mano, tras confirmar "
                    + "la identidad del dueno de la cuenta.", adminEmail);
            return;
        }

        // (c) No existe ningun ADMIN todavia: se crea la persona, el usuario
        // -habilitado, es la unica forma de que sirva de algo- y se le asigna
        // el rol ADMIN. La contrasena se cifra con el MISMO bean
        // PasswordEncoder (Argon2) que usa /auth/register; no se reimplementa
        // nada.
        Persona persona = personaRepository.save(
                Persona.builder()
                        .nombres("Administrador")
                        .apellidos("Del Sistema")
                        .build());

        Role admin = roleRepository.getReferenceById(RolesEnum.ADMIN.getId());

        User usuario = User.builder()
                .persona(persona)
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .enabled(true)
                .roles(new HashSet<>(Set.of(admin)))
                .build();

        userRepository.save(usuario);

        // Nunca la contrasena, ni la de texto plano ni el hash: el log de
        // Actions y del VPS los ve cualquiera con acceso al repositorio.
        log.info("Administrador inicial creado a partir de ADMIN_EMAIL ({}).", adminEmail);
    }

    private static boolean esVacia(String valor) {
        return valor == null || valor.isBlank();
    }
}
