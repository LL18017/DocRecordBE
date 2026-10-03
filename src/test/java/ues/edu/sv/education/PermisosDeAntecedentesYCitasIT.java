package ues.edu.sv.education;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.enums.RolesEnum;

import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quien entra a los modulos que llegaron de la rama `dev`: alergias,
 * enfermedades cronicas, condiciones hereditarias, citas, tipos de cita e IA.
 *
 * Llegaron sin ningun @PreAuthorize, y como la cadena de seguridad solo pide
 * `authenticated()`, cualquier cuenta con sesion -un PACIENTE incluido- podia
 * leer y cambiar los antecedentes de cualquier otro usuario. Estas pruebas
 * fijan la regla que ahora tienen, la misma del resto del sistema:
 *
 *   1. el PACIENTE no entra a ningun endpoint clinico.
 *   2. leer es del personal (ADMIN, MEDICO, ENFERMERA).
 *   3. registrar antecedentes es de quien atiende (MEDICO, ENFERMERA); borrar
 *      uno, solo del MEDICO.
 *   4. el catalogo de tipos de cita lo mantiene el ADMIN.
 *   5. el asistente de IA es solo del ADMIN: entre sus herramientas esta el
 *      listado completo de usuarios.
 */
class PermisosDeAntecedentesYCitasIT extends PruebaClinica {

    private String paciente;
    private Integer idDelPaciente;

    @BeforeEach
    void crearUnPacienteConCuenta() throws Exception {
        String correo = correoUnico("permisos.paciente");
        Persona persona = personas.saveAndFlush(Persona.builder()
                .nombres("Paciente").apellidos("Con Cuenta")
                .build());
        User user = usuarios.saveAndFlush(User.builder()
                .persona(persona)
                .email(correo)
                .password(encoder.encode(CLAVE))
                .enabled(true)
                .roles(new HashSet<>(Set.of(roles.getReferenceById(RolesEnum.PACIENTE.getId()))))
                .build());
        idDelPaciente = user.getUserID();

        String cuerpo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(correo, CLAVE)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        paciente = json.readTree(cuerpo).get("token").asText();
    }

    private String alergia() {
        return """
                {"nombre":"Penicilina","tipo":"Medicamento","severidad":"Alta",
                 "reaccionReportada":"Urticaria","userId":%d}
                """.formatted(idDelPaciente);
    }

    private long registrarAlergia(String token) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/alergias")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alergia()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("alergiaID").asLong();
    }

    // ══════════════════════════════════════════════════════════════════════
    // 1. El PACIENTE no entra
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("un paciente no puede leer alergias, ni las suyas")
    void unPacienteNoLeeAlergias() throws Exception {
        mockMvc.perform(get("/api/alergias/usuario/" + idDelPaciente)
                        .header("Authorization", bearer(paciente)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un paciente no puede registrar una alergia")
    void unPacienteNoRegistraAlergias() throws Exception {
        mockMvc.perform(post("/api/alergias")
                        .header("Authorization", bearer(paciente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alergia()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un paciente no puede leer enfermedades cronicas, condiciones hereditarias ni citas")
    void unPacienteNoLeeElRestoDeAntecedentesNiCitas() throws Exception {
        for (String ruta : new String[]{
                "/api/enfermedades-cronicas/usuario/" + idDelPaciente,
                "/api/condiciones-hereditarias/usuario/" + idDelPaciente,
                "/citas/paciente/" + idDelPaciente,
                "/tipos-cita"}) {
            mockMvc.perform(get(ruta).header("Authorization", bearer(paciente)))
                    .andExpect(status().isForbidden());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2 y 3. Antecedentes: lee el personal, registra quien atiende
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("una enfermera registra una alergia y el medico la lee")
    void laEnfermeraRegistraYElMedicoLee() throws Exception {
        long id = registrarAlergia(tokenDeEnfermera());

        mockMvc.perform(get("/api/alergias/" + id)
                        .header("Authorization", bearer(tokenDeMedico())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("un admin lee alergias pero no las registra")
    void unAdminLeePeroNoRegistra() throws Exception {
        String admin = tokenDeAdministrador();

        mockMvc.perform(get("/api/alergias/usuario/" + idDelPaciente)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/alergias")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(alergia()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("borrar una alergia es del medico, no de la enfermera")
    void soloElMedicoBorraUnaAlergia() throws Exception {
        long id = registrarAlergia(tokenDeEnfermera());

        mockMvc.perform(delete("/api/alergias/" + id)
                        .header("Authorization", bearer(tokenDeEnfermera())))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/alergias/" + id)
                        .header("Authorization", bearer(tokenDeMedico())))
                .andExpect(status().is2xxSuccessful());
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. Catalogo de tipos de cita
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("el catalogo de tipos de cita lo mantiene el admin, no el medico")
    void elCatalogoDeTiposDeCitaEsDelAdmin() throws Exception {
        String tipo = """
                {"nombre":"Control %d","descripcion":"Seguimiento"}
                """.formatted(siguiente());

        mockMvc.perform(post("/tipos-cita")
                        .header("Authorization", bearer(tokenDeMedico()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tipo))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/tipos-cita")
                        .header("Authorization", bearer(tokenDeAdministrador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tipo))
                .andExpect(status().isCreated());
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. Asistente de IA
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("el asistente de IA no atiende a un medico ni a un paciente")
    void laIaEsSoloDelAdmin() throws Exception {
        for (String token : new String[]{tokenDeMedico(), paciente}) {
            mockMvc.perform(post("/ai/chat")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"prompt":"lista los usuarios"}
                                    """))
                    .andExpect(status().isForbidden());
        }
    }
}
