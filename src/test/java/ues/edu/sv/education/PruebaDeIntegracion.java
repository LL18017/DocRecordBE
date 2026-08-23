package ues.edu.sv.education;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Base de las pruebas de integracion: levanta el contexto de Spring contra una
 * base de datos PostgreSQL REAL, dedicada a pruebas, que se recrea desde cero
 * en cada ejecucion.
 *
 * ── Por que una base real y no H2 ─────────────────────────────────────────
 * Lo que sostiene este modelo son restricciones del motor, no codigo Java: el
 * UNIQUE de persona.dui, el ON DELETE CASCADE hacia las especializaciones y los
 * CHECK de sexo y tipo_sangre viven en la migracion de Flyway. Verificarlos
 * contra H2 comprobaria el dialecto de H2, no el de PostgreSQL, que es el que
 * se despliega.
 *
 * ── Por que no Testcontainers ─────────────────────────────────────────────
 * Se intento primero, que es lo ideal porque da un contenedor desechable por
 * corrida. No funciona en este entorno: Docker 29.1.3 (el que trae Ubuntu
 * 26.04) exige API minima 1.44, y la libreria docker-java 3.4.2 que arrastra
 * Testcontainers negocia 1.32. Falla con "client version 1.32 is too old".
 * Forzar la version por DOCKER_API_VERSION, por -Dapi.version y subiendo
 * Testcontainers a 1.21.3 no lo resuelve. Cuando Testcontainers publique una
 * version compatible con Docker 29, esta clase se sustituye por un
 * @Container y el resto de las pruebas no cambia.
 *
 * ── Que se conserva y que se pierde ───────────────────────────────────────
 * Se conserva lo importante: PostgreSQL de verdad, y las migraciones aplicadas
 * desde una base vacia en CADA ejecucion, porque el bloque estatico de abajo
 * borra y recrea la base antes de que Spring levante. Es decir, cada corrida
 * tambien verifica que V1..Vn aplican limpio sobre una base nueva.
 * Se pierde el aislamiento total frente al entorno: hace falta que el
 * PostgreSQL del docker-compose este arriba.
 *
 *   docker compose up -d      (desde DocRecordBE/)
 */
@SpringBootTest
public abstract class PruebaDeIntegracion {

    private static final String HOST = System.getProperty("test.db.host", "localhost");
    private static final String PUERTO = System.getProperty("test.db.port", "5432");
    private static final String USUARIO = System.getProperty("test.db.user", "postgres");
    private static final String CLAVE = System.getProperty("test.db.password", "9902");
    private static final String BASE_PRUEBAS = "datadoc_test";

    private static final String URL_ADMIN =
            "jdbc:postgresql://" + HOST + ":" + PUERTO + "/postgres";
    private static final String URL_PRUEBAS =
            "jdbc:postgresql://" + HOST + ":" + PUERTO + "/" + BASE_PRUEBAS;

    static {
        recrearBaseDePruebas();
    }

    /**
     * Borra y vuelve a crear la base de pruebas antes de que arranque Spring.
     * Asi cada ejecucion parte de cero y Flyway aplica todas las migraciones,
     * que es parte de lo que se quiere verificar.
     */
    private static void recrearBaseDePruebas() {
        try (Connection conexion = DriverManager.getConnection(URL_ADMIN, USUARIO, CLAVE);
             Statement sentencia = conexion.createStatement()) {

            // Cierra sesiones abiertas contra la base de pruebas; si quedo una
            // de una ejecucion anterior, el DROP se quedaria esperando.
            sentencia.execute(
                    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity "
                            + "WHERE datname = '" + BASE_PRUEBAS + "' AND pid <> pg_backend_pid()");
            sentencia.execute("DROP DATABASE IF EXISTS " + BASE_PRUEBAS);
            sentencia.execute("CREATE DATABASE " + BASE_PRUEBAS);

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudo preparar la base de pruebas en " + URL_ADMIN + ".\n"
                            + "Las pruebas de integracion necesitan el PostgreSQL del compose corriendo:\n"
                            + "    cd DocRecordBE && docker compose up -d\n"
                            + "Causa: " + e.getMessage(), e);
        }
    }

    @DynamicPropertySource
    static void apuntarALaBaseDePruebas(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> URL_PRUEBAS);
        registro.add("spring.datasource.username", () -> USUARIO);
        registro.add("spring.datasource.password", () -> CLAVE);
    }
}
