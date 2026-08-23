package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.repository.PersonaRepository;
import ues.edu.sv.education.repository.UserRepository;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CRUD de pacientes a traves de la API real.
 *
 * Se prueba por HTTP y no llamando al servicio: lo que se quiere verificar son
 * las reglas tal como las ve el frontend, incluidos los codigos de estado. Un
 * 409 que el servicio lanza pero el manejador de errores convierte en 500 seria
 * un fallo invisible desde el servicio y visible desde aqui -- ya paso una vez
 * en este proyecto.
 */
@AutoConfigureMockMvc
class PacienteCrudIT extends PruebaDeIntegracion {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository usuarios;
    @Autowired private PersonaRepository personas;

    private final ObjectMapper json = new ObjectMapper();

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final String CLAVE = "Docrecord2026!";

    private String token;

    @BeforeEach
    void autenticarUnMedico() throws Exception {
        String correo = "crud.medico." + CONTADOR.incrementAndGet() + "@ues.edu.sv";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Medico","apellidos":"Del Crud",
                                 "email":"%s","password":"%s","especialidadId":1}
                                """.formatted(correo, CLAVE)))
                .andExpect(status().is2xxSuccessful());

        User usuario = usuarios.findByEmailContainingIgnoreCase(correo)
                .orElseThrow(() -> new AssertionError("el registro no creo el usuario"));
        usuario.setEnabled(true);
        usuarios.saveAndFlush(usuario);

        String cuerpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(correo, CLAVE)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();

        token = json.readTree(cuerpo).get("token").asText();
    }

    /** Crea un paciente con DUI unico y devuelve la respuesta ya deserializada. */
    private JsonNode crearPaciente(String dui, String nombres, String apellidos) throws Exception {
        String cuerpo = mockMvc.perform(post("/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"persona":{"dui":"%s","nombres":"%s","apellidos":"%s",
                                            "fechaNacimiento":"1990-05-20","sexo":"F",
                                            "telefono":"7777-0000","direccion":"San Salvador"},
                                 "tipoSangre":"O+"}
                                """.formatted(dui, nombres, apellidos)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private String duiUnico() {
        return String.format("%08d-1", CONTADOR.incrementAndGet() + 10_000_000);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Expediente correlativo
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("el expediente lo genera el servidor, no el cliente")
    void elExpedienteLoGeneraElServidor() throws Exception {
        JsonNode creado = crearPaciente(duiUnico(), "Expediente", "Generado");

        String expediente = creado.get("expediente").asText();

        assertTrue(expediente.matches("EXP-\\d{6}"),
                "Se esperaba el formato EXP-000001, se obtuvo: " + expediente);
    }

    @Test
    @DisplayName("dos altas seguidas reciben expedientes distintos y consecutivos")
    void dosAltasRecibenExpedientesDistintos() throws Exception {
        // El correlativo sale de una secuencia de PostgreSQL, no de MAX+1: dos
        // altas simultaneas con MAX+1 leerian el mismo maximo y chocarian
        // contra el UNIQUE de la columna.
        int primero = numeroDeExpediente(crearPaciente(duiUnico(), "Primera", "Alta"));
        int segundo = numeroDeExpediente(crearPaciente(duiUnico(), "Segunda", "Alta"));

        assertNotEquals(primero, segundo, "dos pacientes no pueden compartir expediente");
        assertEquals(primero + 1, segundo, "el correlativo debe avanzar de uno en uno");
    }

    @Test
    @DisplayName("el expediente que mande el cliente se ignora")
    void elExpedienteDelClienteSeIgnora() throws Exception {
        // El campo salio del DTO, pero un cliente viejo podria seguir mandandolo.
        // Debe ignorarse, no respetarse: si se respetara, volveria el problema de
        // los numeros inventados y las colisiones.
        String cuerpo = mockMvc.perform(post("/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"persona":{"dui":"%s","nombres":"Cliente","apellidos":"Terco",
                                            "fechaNacimiento":"1985-02-02","sexo":"M"},
                                 "expediente":"YO-ELIJO-1",
                                 "tipoSangre":"A+"}
                                """.formatted(duiUnico())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertNotEquals("YO-ELIJO-1", json.readTree(cuerpo).get("expediente").asText());
    }

    private int numeroDeExpediente(JsonNode paciente) {
        return Integer.parseInt(paciente.get("expediente").asText().substring("EXP-".length()));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Actualizar: completar sin destruir
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("actualizar solo el telefono no borra los demas datos")
    void actualizarSoloElTelefonoNoBorraLoDemas() throws Exception {
        // Es la regla central del PUT. Sin ella, un formulario que edita un solo
        // campo dejaria el resto del expediente en blanco.
        JsonNode creado = crearPaciente(duiUnico(), "Sin", "Perdidas");
        long personaId = creado.get("personaId").asLong();

        String cuerpo = mockMvc.perform(put("/pacientes/{id}", personaId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"persona\":{\"telefono\":\"7000-9999\"}}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode actualizado = json.readTree(cuerpo);
        JsonNode persona = actualizado.get("persona");

        assertEquals("7000-9999", persona.get("telefono").asText(), "el telefono debio cambiar");
        assertEquals("Sin", persona.get("nombres").asText(), "los nombres no debieron tocarse");
        assertEquals("Perdidas", persona.get("apellidos").asText());
        assertEquals("San Salvador", persona.get("direccion").asText(),
                "la direccion no venia en la peticion y no debio borrarse");
        assertEquals("1990-05-20", persona.get("fechaNacimiento").asText());
    }

    @Test
    @DisplayName("actualizar no puede cambiar el numero de expediente")
    void actualizarNoCambiaElExpediente() throws Exception {
        // Un expediente clinico ya emitido puede estar referenciado en papel.
        JsonNode creado = crearPaciente(duiUnico(), "Expediente", "Inmutable");
        long personaId = creado.get("personaId").asLong();
        String original = creado.get("expediente").asText();

        String cuerpo = mockMvc.perform(put("/pacientes/{id}", personaId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"persona\":{},\"expediente\":\"OTRO-999\",\"tipoSangre\":\"B+\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(original, json.readTree(cuerpo).get("expediente").asText());
    }

    @Test
    @DisplayName("actualizar con un DUI distinto se rechaza en vez de pisarlo")
    void actualizarConDuiDistintoSeRechaza() throws Exception {
        // El DUI es la identidad que sostiene el mecanismo de no duplicar
        // personas. Cambiarlo en silencio convertiria a esta persona en otra.
        JsonNode creado = crearPaciente(duiUnico(), "Identidad", "Fija");
        long personaId = creado.get("personaId").asLong();

        mockMvc.perform(put("/pacientes/{id}", personaId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"persona\":{\"dui\":\"99999999-9\"}}"))
                .andExpect(status().isConflict());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Baja
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("dar de baja a un paciente conserva a la persona")
    void darDeBajaConservaALaPersona() throws Exception {
        // Esa identidad puede ser ademas medico o enfermera. Dejar de ser
        // paciente no es dejar de existir; borrar la persona arrastraria sus
        // otros papeles.
        JsonNode creado = crearPaciente(duiUnico(), "Se Da", "De Baja");
        long personaId = creado.get("personaId").asLong();

        mockMvc.perform(delete("/pacientes/{id}", personaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/pacientes/{id}", personaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        assertTrue(personas.findById(personaId).isPresent(),
                "la persona debe seguir existiendo tras dejar de ser paciente");
    }

    @Test
    @DisplayName("ver, actualizar o borrar un paciente inexistente responde 404")
    void unPacienteInexistenteResponde404() throws Exception {
        long inexistente = 9_999_999L;

        mockMvc.perform(get("/pacientes/{id}", inexistente)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/pacientes/{id}", inexistente)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/pacientes/{id}", inexistente)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"persona\":{\"telefono\":\"7000-0000\"}}"))
                .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Reutilizar identidad
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("registrar dos veces a la misma persona como paciente responde 409")
    void registrarDosVecesAlMismoPacienteResponde409() throws Exception {
        JsonNode creado = crearPaciente(duiUnico(), "Ya Es", "Paciente");
        long personaId = creado.get("personaId").asLong();

        mockMvc.perform(post("/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"persona\":{\"personaId\":%d},\"tipoSangre\":\"O+\"}"
                                .formatted(personaId)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("una persona incompleta necesita fecha y sexo para volverse paciente")
    void unaPersonaIncompletaNecesitaFechaYSexo() throws Exception {
        // Es el caso que se detecto usando la pantalla: un medico registrado por
        // /auth/register tiene persona con solo nombres y apellidos. Al llegar
        // como paciente debe poder completarse en la MISMA llamada, no quedar
        // bloqueado.
        var persona = personas.saveAndFlush(
                ues.edu.sv.education.model.entity.Persona.builder()
                        .dui(duiUnico())
                        .nombres("Medico").apellidos("Que Se Atiende")
                        .build());

        // Sin completar: 422 con mensaje explicito.
        mockMvc.perform(post("/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"persona\":{\"personaId\":%d},\"tipoSangre\":\"O+\"}"
                                .formatted(persona.getPersonaId())))
                .andExpect(status().is(422));

        // Completando en la misma llamada: 201, reutilizando la persona.
        String cuerpo = mockMvc.perform(post("/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"persona":{"personaId":%d,"fechaNacimiento":"1988-08-08","sexo":"M"},
                                 "tipoSangre":"O+"}
                                """.formatted(persona.getPersonaId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertEquals(persona.getPersonaId(), json.readTree(cuerpo).get("personaId").asLong(),
                "debe reutilizar la persona existente, no crear una nueva");
    }

    // ══════════════════════════════════════════════════════════════════════
    // Busqueda
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("la busqueda funciona por apellido y por DUI, y sin filtro devuelve todo")
    void laBusquedaFuncionaPorApellidoYPorDui() throws Exception {
        String dui = duiUnico();
        crearPaciente(dui, "Buscable", "Apellidoraro");

        // Sin parametro. Fallaba con 500 ("function lower(bytea) does not
        // exist") cuando el filtro llegaba null a la consulta.
        mockMvc.perform(get("/pacientes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String porApellido = mockMvc.perform(get("/pacientes").param("buscar", "Apellidoraro")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(json.readTree(porApellido).size() >= 1, "debio encontrarlo por apellido");

        String porDui = mockMvc.perform(get("/pacientes").param("buscar", dui)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(json.readTree(porDui).size() >= 1, "debio encontrarlo por DUI");
    }
}
