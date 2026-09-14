package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import ues.edu.sv.education.model.entity.User;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrencia DE VERDAD: peticiones HTTP reales en hilos separados, no
 * llamadas secuenciales disfrazadas de "concurrentes".
 *
 * Se extiende PruebaConServidorReal (no PruebaClinica/MockMvc) a proposito:
 * MockMvc invoca el DispatcherServlet directo, sin abrir conexiones ni pasar
 * por Tomcat, y aunque puede llamarse desde varios hilos, dos hilos que
 * comparten el mismo proceso de prueba no ejercitan la misma superposicion de
 * transacciones que dos conexiones de verdad. Aqui cada peticion es un socket
 * TCP real contra el puerto aleatorio del servidor levantado por la prueba.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrenciaIT extends PruebaConServidorReal {

    private final ObjectMapper json = new ObjectMapper();

    // Estatico: cada @Test de JUnit 5 crea una INSTANCIA nueva de la clase
    // (lifecycle PER_METHOD, el default), asi que un contador de instancia se
    // reiniciaria a 0 en cada prueba. Como el registro por /auth/register
    // COMITEA de verdad (no hay rollback entre pruebas de esta clase), dos
    // pruebas que reinician su contador generarian el mismo correo y la
    // segunda chocaria con 409 "correo ya registrado".
    private static final AtomicInteger contador = new AtomicInteger();

    // ══════════════════════════════════════════════════════════════════════
    // Dos altas de paciente a la vez
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("varias altas de paciente concurrentes reciben expedientes distintos, sin duplicados ni fallos")
    void altasDePacienteConcurrentesNoDuplicanElExpediente() throws Exception {
        String token = registrarMedicoYObtenerToken();

        // 8 peticiones POST /pacientes lanzadas a la vez desde 8 hilos
        // distintos, cada una con su propia conexion HTTP. El expediente lo
        // entrega expediente_seq (una secuencia de PostgreSQL, ver V5): en
        // teoria no puede duplicarse aunque dos transacciones la pidan al
        // mismo tiempo. Esta prueba deja de ser teoria.
        int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        List<Callable<HttpResponse<String>>> tareas = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int idx = i;
            tareas.add(() -> crearPacienteHttp(token, idx));
        }

        List<Future<HttpResponse<String>>> resultados = pool.invokeAll(tareas);
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "el pool no termino a tiempo");

        List<String> expedientes = new ArrayList<>();
        for (Future<HttpResponse<String>> f : resultados) {
            HttpResponse<String> r = f.get();
            assertEquals(201, r.statusCode(),
                    "cada alta concurrente debe crearse sin chocar: " + r.body());
            expedientes.add(json.readTree(r.body()).get("expediente").asText());
        }

        assertEquals(n, new HashSet<>(expedientes).size(),
                "hay expedientes duplicados entre altas concurrentes: " + expedientes);
    }

    private HttpResponse<String> crearPacienteHttp(String token, int idx) throws Exception {
        String dui = String.format("%08d-4", 70_000_000 + contador.incrementAndGet() * 100 + idx);
        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + puerto + "/pacientes"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"persona":{"dui":"%s","nombres":"Concurrente","apellidos":"Numero %d",
                                    "fechaNacimiento":"1990-01-01","sexo":"F"}}
                        """.formatted(dui, idx)))
                .build();
        try (HttpClient cliente = HttpClient.newHttpClient()) {
            return cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Emitir una receta mientras se borra la consulta que la origina
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("emitir una receta a la vez que se borra su consulta nunca responde 500")
    void emitirRecetaMientrasSeBorraSuConsultaNuncaDa500() throws Exception {
        // PrescripcionService.crear primero LEE la consulta y despues INSERTA
        // la receta, en la misma transaccion pero sin bloquear la fila. Si otra
        // transaccion borra esa consulta y comitea justo entre esas dos
        // acciones, el INSERT choca contra la FK (prescripciones.consulta_id
        // REFERENCES consultas ON DELETE CASCADE, ver V6). Eso debe traducirse
        // en un DataIntegrityViolationException -> 409 (GlobalExceptionHandler),
        // nunca en un 500 crudo. Se repite varias veces porque una sola
        // carrera no garantiza el entrelazado exacto que hace falta para
        // disparar el conflicto.
        String token = registrarMedicoConFilaYObtenerToken();
        HttpResponse<String> altaPaciente = crearPacienteHttp(token, 999);
        assertEquals(201, altaPaciente.statusCode(),
                "no se pudo preparar el paciente de la prueba: " + altaPaciente.body());
        long pacienteId = json.readTree(altaPaciente.body()).get("personaId").asLong();

        // El triage es requisito para abrir consulta. Se toma UNA vez y vale
        // para las 15 iteraciones: la ventana son 24 horas, no una consulta.
        String tokenEnfermera = crearEnfermeraYObtenerToken();
        tomarSignosVitalesHttp(tokenEnfermera, pacienteId);

        for (int intento = 0; intento < 15; intento++) {
            long consultaId = crearConsultaHttp(token, pacienteId);

            ExecutorService pool = Executors.newFixedThreadPool(2);
            Future<HttpResponse<String>> futuroBorrado = pool.submit(() -> borrarConsultaHttp(token, consultaId));
            Future<HttpResponse<String>> futuroReceta = pool.submit(() -> emitirRecetaHttp(token, consultaId));

            HttpResponse<String> rBorrado = futuroBorrado.get();
            HttpResponse<String> rReceta = futuroReceta.get();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "el pool no termino a tiempo");

            assertTrue(rBorrado.statusCode() < 500,
                    "borrar la consulta (intento " + intento + ") respondio 500: " + rBorrado.body());
            assertTrue(rReceta.statusCode() < 500,
                    "emitir la receta (intento " + intento + ") respondio 500: " + rReceta.body());
        }
    }

    private String registrarMedicoYObtenerToken() throws Exception {
        String correo = "conc.medico." + contador.incrementAndGet() + "@ues.edu.sv";
        HttpRequest registro = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + puerto + "/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"nombres":"Medico","apellidos":"Concurrente","email":"%s",
                         "password":"%s","especialidadId":1}
                        """.formatted(correo, CLAVE)))
                .build();
        try (HttpClient cliente = HttpClient.newHttpClient()) {
            HttpResponse<String> r = cliente.send(registro, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) {
                throw new AssertionError("el registro del medico fallo: " + r.body());
            }
        }

        User usuario = usuarios.findByEmailIgnoreCase(correo)
                .orElseThrow(() -> new AssertionError("el registro no creo el usuario"));
        usuario.setEnabled(true);
        usuarios.saveAndFlush(usuario);

        HttpResponse<String> login = iniciarSesion(correo);
        return json.readTree(login.body()).get("token").asText();
    }

    /** Igual que registrarMedicoYObtenerToken: /auth/register ya crea la fila en `medicos`. */
    private String registrarMedicoConFilaYObtenerToken() throws Exception {
        return registrarMedicoYObtenerToken();
    }

    /**
     * Da de alta una enfermera y devuelve su token.
     *
     * Hace falta porque el triage es ahora REQUISITO de la consulta (ver
     * ConsultaService.crear) y solo enfermeria puede registrar constantes. El
     * rol se cambia a mano despues de /auth/register -que solo crea MEDICOS-
     * y la fila de `enfermeras` se inserta por JDBC: es montaje de la prueba,
     * no lo que se esta midiendo.
     */
    private String crearEnfermeraYObtenerToken() throws Exception {
        String correo = "conc.enfermera." + contador.incrementAndGet() + "@ues.edu.sv";
        HttpRequest registro = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + puerto + "/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"nombres":"Enfermera","apellidos":"Concurrente","email":"%s",
                         "password":"%s","especialidadId":1}
                        """.formatted(correo, CLAVE)))
                .build();
        try (HttpClient cliente = HttpClient.newHttpClient()) {
            HttpResponse<String> r = cliente.send(registro, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) {
                throw new AssertionError("el registro de la enfermera fallo: " + r.body());
            }
        }

        User usuario = usuarios.findByEmailIgnoreCase(correo)
                .orElseThrow(() -> new AssertionError("el registro no creo el usuario"));
        usuario.setEnabled(true);
        usuarios.saveAndFlush(usuario);

        Long personaId = usuario.getPersona().getPersonaId();
        jdbc.update("delete from user_roles where user_id = ?", usuario.getUserID());
        jdbc.update("insert into user_roles (user_id, role_id) values (?, 3)", usuario.getUserID());
        jdbc.update("delete from medicos where persona_id = ?", personaId);
        jdbc.update("insert into enfermeras (persona_id, activo) values (?, true)", personaId);

        return json.readTree(iniciarSesion(correo).body()).get("token").asText();
    }

    /** Constantes minimas: el backend exige al menos una medida. */
    private void tomarSignosVitalesHttp(String tokenEnfermera, long pacienteId) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + puerto + "/signos-vitales"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + tokenEnfermera)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"pacienteId":%d,"pulsoLpm":72}
                        """.formatted(pacienteId)))
                .build();
        try (HttpClient cliente = HttpClient.newHttpClient()) {
            HttpResponse<String> r = cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 201) {
                throw new AssertionError("no se pudieron tomar las constantes: " + r.body());
            }
        }
    }

    private long crearConsultaHttp(String token, long pacienteId) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + puerto + "/consultas"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"pacienteId":%d,"motivo":"Control de la prueba de concurrencia"}
                        """.formatted(pacienteId)))
                .build();
        try (HttpClient cliente = HttpClient.newHttpClient()) {
            HttpResponse<String> r = cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 201) {
                throw new AssertionError("no se pudo crear la consulta de la prueba: " + r.body());
            }
            return json.readTree(r.body()).get("consultaId").asLong();
        }
    }

    private HttpResponse<String> borrarConsultaHttp(String token, long consultaId) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + puerto + "/consultas/" + consultaId))
                .header("Authorization", "Bearer " + token)
                .DELETE()
                .build();
        try (HttpClient cliente = HttpClient.newHttpClient()) {
            return cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
        }
    }

    private HttpResponse<String> emitirRecetaHttp(String token, long consultaId) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + puerto + "/prescripciones"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"consultaId":%d,"medicamentos":[{"medicamento":"Receta de la carrera"}]}
                        """.formatted(consultaId)))
                .build();
        try (HttpClient cliente = HttpClient.newHttpClient()) {
            return cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
        }
    }
}
