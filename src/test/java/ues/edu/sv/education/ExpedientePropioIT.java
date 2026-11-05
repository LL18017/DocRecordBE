package ues.edu.sv.education;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import ues.edu.sv.education.model.entity.Enfermera;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.enums.RolesEnum;
import ues.edu.sv.education.service.paciente.ExpedientePropio;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nadie escribe en su propio expediente clinico (ver ExpedientePropio).
 *
 * Una cuenta puede ser personal y paciente a la vez. Aqui se arma cada caso
 * -medico, enfermera y administrador que tambien son pacientes- y se
 * comprueba que leer lo suyo sigue permitido, que escribir en lo clinico
 * responde 403 con el mensaje de la regla, y que con cualquier otro paciente
 * nada cambia.
 */
class ExpedientePropioIT extends PruebaClinica {

    private static final String MOTIVO = "{\"motivo\":\"Consulta registrada al paciente equivocado\"}";

    @Autowired private JdbcTemplate jdbc;

    private String otroMedico;

    @BeforeEach
    void preparar() throws Exception {
        otroMedico = tokenDeMedico();
    }

    // ── Medico y paciente ─────────────────────────────────────────────────

    @Test
    @DisplayName("el medico que tambien es paciente no se registra consultas ni alergias; a otro paciente si")
    void elMedicoNoSeAtiendeASiMismo() throws Exception {
        Cuenta yo = medicoQueEsPaciente();
        tomarSignosVitales(yo.persona());

        propio(mockMvc.perform(post("/consultas")
                .header("Authorization", bearer(yo.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pacienteId":%d,"motivo":"Control general","diagnostico":"Control sin hallazgos"}
                        """.formatted(yo.persona()))));
        propio(mockMvc.perform(post("/alergias")
                .header("Authorization", bearer(yo.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pacienteId":%d,"sustancia":"Penicilina","reaccion":"Urticaria",
                         "severidad":"LEVE","fechaDeteccion":"2021-07-02"}
                        """.formatted(yo.persona()))));

        // Con otro paciente, todo igual que siempre.
        crearConsultaSimple(yo.token(), crearPaciente(yo.token()));
    }

    @Test
    @DisplayName("lo que un colega le registro, el medico-paciente no lo edita ni lo anula")
    void noTocaLoQueOtroLeRegistro() throws Exception {
        Cuenta yo = medicoQueEsPaciente();
        long consultaId = crearConsultaSimple(otroMedico, yo.persona()).get("consultaId").asLong();

        propio(mockMvc.perform(put("/consultas/{id}", consultaId)
                .header("Authorization", bearer(yo.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Me lo cambio yo\"}")));
        propio(mockMvc.perform(post("/consultas/{id}/anular", consultaId)
                .header("Authorization", bearer(yo.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(MOTIVO)));
    }

    @Test
    @DisplayName("leer su propio expediente y corregir sus datos de identidad si puede")
    void leerYCorregirSusDatosSiPuede() throws Exception {
        Cuenta yo = medicoQueEsPaciente();
        crearConsultaSimple(otroMedico, yo.persona());

        mockMvc.perform(get("/consultas").param("pacienteId", String.valueOf(yo.persona()))
                        .header("Authorization", bearer(yo.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/pacientes/{id}", yo.persona()).header("Authorization", bearer(yo.token())))
                .andExpect(status().isOk());

        // El nombre no es un acto clinico: es lo que hace falta corregir en una
        // cuenta que nacio con un nombre provisional.
        mockMvc.perform(put("/pacientes/{id}", yo.persona())
                        .header("Authorization", bearer(yo.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"persona\":{\"nombres\":\"Nombre Corregido\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persona.nombres").value("Nombre Corregido"));
    }

    // ── Enfermera y paciente ──────────────────────────────────────────────

    @Test
    @DisplayName("la enfermera que tambien es paciente no se toma sus propios signos vitales; a otro paciente si")
    void laEnfermeraNoSeTomaSusSignos() throws Exception {
        Persona persona = personas.saveAndFlush(Persona.builder().nombres("Enfermera").apellidos("Que Se Atiende").build());
        String correo = correoUnico("propio.enfermera");
        cuenta(persona, correo, RolesEnum.ENFERMERA);
        enfermeras.saveAndFlush(Enfermera.builder().persona(persona).activo(true).build());
        hacerPaciente(persona.getPersonaId());
        String token = iniciarSesion(correo);

        propio(signos(token, persona.getPersonaId()));

        signos(token, crearPaciente(token)).andExpect(status().isCreated());
    }

    // ── Administrador y paciente ──────────────────────────────────────────

    @Test
    @DisplayName("el administrador que tambien es paciente no aprueba ni rechaza la anulacion de su propia consulta; otro admin si")
    void elAdminNoResuelveLoSuyo() throws Exception {
        Persona persona = personas.saveAndFlush(Persona.builder().nombres("Admin").apellidos("Que Se Atiende").build());
        String correo = correoUnico("propio.admin");
        cuenta(persona, correo, RolesEnum.ADMIN);
        hacerPaciente(persona.getPersonaId());
        String admin = iniciarSesion(correo);

        long consultaId = crearConsultaSimple(otroMedico, persona.getPersonaId()).get("consultaId").asLong();
        jdbc.update("UPDATE consultas SET registrada_en = now() - interval '1 hour' WHERE consulta_id = ?", consultaId);
        long solicitudId = json.readTree(mockMvc.perform(post("/consultas/{id}/anular", consultaId)
                        .header("Authorization", bearer(otroMedico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MOTIVO))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString()).get("solicitudId").asLong();

        mockMvc.perform(post("/anulaciones/{id}/aprobar", solicitudId).header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ExpedientePropio.MENSAJE_ANULACION));
        mockMvc.perform(post("/anulaciones/{id}/rechazar", solicitudId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"La consulta es correcta, no procede\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ExpedientePropio.MENSAJE_ANULACION));

        mockMvc.perform(post("/anulaciones/{id}/aprobar", solicitudId).header("Authorization", bearer(tokenDeAdministrador())))
                .andExpect(status().isOk());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    private record Cuenta(long persona, String token) { }

    /** Un medico con ficha que ademas tiene expediente, registrado por un colega. */
    private Cuenta medicoQueEsPaciente() throws Exception {
        String correo = correoUnico("propio.medico");
        User medico = crearMedicoHabilitado(correo, CLAVE, "Medico", "Que Se Atiende");
        hacerPaciente(medico.getPersona().getPersonaId());
        return new Cuenta(medico.getPersona().getPersonaId(), iniciarSesion(correo));
    }

    /** Le abre expediente a una persona que ya existe, como Pacientes al encontrarla por DUI. */
    private void hacerPaciente(long personaId) throws Exception {
        mockMvc.perform(post("/pacientes")
                        .header("Authorization", bearer(otroMedico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"persona":{"personaId":%d,"dui":"%s","fechaNacimiento":"%s","sexo":"M"}}
                                """.formatted(personaId, duiUnico(), LocalDate.of(1985, 1, 10))))
                .andExpect(status().isCreated());
    }

    private void cuenta(Persona persona, String correo, RolesEnum rol) {
        usuarios.saveAndFlush(User.builder()
                .persona(persona)
                .email(correo)
                .password(encoder.encode(CLAVE))
                .enabled(true)
                .roles(new HashSet<>(Set.of(roles.getReferenceById(rol.getId()))))
                .build());
    }

    private ResultActions signos(String token, long pacienteId) throws Exception {
        return mockMvc.perform(post("/signos-vitales")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pacienteId\":%d,\"pulsoLpm\":72}".formatted(pacienteId)));
    }

    /** 403 con el mensaje de la regla, no otro 403 cualquiera. */
    private void propio(ResultActions resultado) throws Exception {
        resultado.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ExpedientePropio.MENSAJE));
    }
}
