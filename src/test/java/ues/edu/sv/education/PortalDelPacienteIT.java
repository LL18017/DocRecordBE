package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.enums.EstadoConsulta;
import ues.edu.sv.education.model.enums.EstadoPrescripcion;
import ues.edu.sv.education.model.enums.RolesEnum;
import ues.edu.sv.education.model.enums.SeveridadDeAlergia;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-34 (DRS-101) · Portal del paciente.
 *
 * El paciente entra con la cuenta que le abre el personal desde su expediente
 * (invitacion, igual que el medico en HU-02) y ve SU expediente en solo
 * lectura. Cada prueba recorre el camino entero con tokens reales: el paciente
 * se registra, se le da acceso al portal por la API, canjea la invitacion e
 * inicia sesion. Lo unico que se monta por debajo de la API son las citas,
 * porque agendarlas exige horarios de medico que no son lo que se prueba.
 */
class PortalDelPacienteIT extends PruebaClinica {

    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");

    @Autowired private JdbcTemplate jdbc;

    private String medico;
    private User cuentaDelMedico;
    private String enfermera;

    @BeforeEach
    void prepararElPersonal() throws Exception {
        String correo = correoUnico("portal.medico");
        cuentaDelMedico = crearMedicoHabilitado(correo, CLAVE, "Carlos", "Menjívar");
        medico = iniciarSesion(correo);
        enfermera = tokenDeEnfermera();
    }

    // ══════════════════════════════════════════════════════════════════════
    // c1 · el paciente ve sus alergias, consultas, recetas y proximas citas
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("c1: el paciente ve sus alergias, consultas (con diagnostico), recetas y proximas citas")
    void elPacienteVeSuExpediente() throws Exception {
        long paciente = crearPaciente(medico);
        registrarAlergia(paciente, "Penicilina", "SEVERA");
        registrarAlergia(paciente, "Polen", "LEVE");

        JsonNode consulta = crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Dolor de garganta","diagnostico":"Faringitis aguda",
                 "exploracionFisica":"Amigdalas inflamadas","indicaciones":"Reposo y liquidos"}
                """.formatted(paciente));
        long receta = emitirReceta(consulta.get("consultaId").asLong());

        int tipo = tipoDeCita();
        LocalDateTime manana = LocalDate.now(ZONA).plusDays(1).atTime(10, 0);
        agendarPorDebajo(paciente, tipo, manana, "AGENDADA");
        agendarPorDebajo(paciente, tipo, manana.plusHours(1), "CANCELADA");
        agendarPorDebajo(paciente, tipo, LocalDate.now(ZONA).minusDays(3).atTime(9, 0), "AGENDADA");

        String token = tokenDelPaciente(paciente);
        JsonNode exp = miExpediente(token);

        assertEquals(paciente, exp.get("paciente").get("personaId").asLong());
        assertTrue(exp.get("paciente").get("expediente").asText().startsWith("EXP-"));

        JsonNode alergias = exp.get("alergias");
        assertEquals(2, alergias.size());
        assertEquals("Penicilina", alergias.get(0).get("sustancia").asText(), "las severas primero");

        JsonNode consultas = exp.get("consultas");
        assertEquals(1, consultas.size());
        // Al personal de enfermeria el diagnostico se le recorta (HU-20 c4);
        // al dueño del expediente no.
        assertEquals("Faringitis aguda", consultas.get(0).get("diagnostico").asText());
        assertEquals("Amigdalas inflamadas", consultas.get(0).get("exploracionFisica").asText());

        JsonNode recetas = exp.get("recetas");
        assertEquals(1, recetas.size());
        assertEquals(receta, recetas.get(0).get("prescripcionId").asLong());
        assertEquals("Oral", recetas.get(0).get("medicamentos").get(0).get("viaAdministracion").asText());

        JsonNode citas = exp.get("proximasCitas");
        assertEquals(1, citas.size(), "ni la cancelada ni la pasada son proximas: " + citas);
        assertEquals("AGENDADA", citas.get(0).get("estado").asText());

        assertEquals(72, exp.get("ultimosSignosVitales").get("pulsoLpm").asInt());
    }

    @Test
    @DisplayName("c1: las consultas y recetas anuladas se ven, marcadas y con su motivo")
    void lasAnuladasSeVenMarcadas() throws Exception {
        long paciente = crearPaciente(medico);
        long vigente = crearConsultaSimple(medico, paciente).get("consultaId").asLong();
        long anulada = crearConsultaSimple(medico, paciente).get("consultaId").asLong();
        anular("/consultas/{id}/anular", anulada, "Se registro al paciente equivocado");

        long recetaVigente = emitirReceta(vigente);
        long recetaAnulada = emitirReceta(vigente);
        anular("/prescripciones/{id}/anular", recetaAnulada, "Dosis mal escrita en la receta");

        JsonNode exp = miExpediente(tokenDelPaciente(paciente));

        JsonNode consultaAnulada = porId(exp.get("consultas"), "consultaId", anulada);
        assertEquals(EstadoConsulta.ANULADA.name(), consultaAnulada.get("estado").asText());
        assertEquals("Se registro al paciente equivocado", consultaAnulada.get("motivoAnulacion").asText());
        assertEquals(EstadoConsulta.FINALIZADA.name(),
                porId(exp.get("consultas"), "consultaId", vigente).get("estado").asText());

        assertEquals(EstadoPrescripcion.ANULADA.name(),
                porId(exp.get("recetas"), "prescripcionId", recetaAnulada).get("estado").asText());
        assertEquals(EstadoPrescripcion.EMITIDA.name(),
                porId(exp.get("recetas"), "prescripcionId", recetaVigente).get("estado").asText());
    }

    @Test
    @DisplayName("c1: un paciente recien registrado ve su expediente vacio, no un error")
    void expedienteVacio() throws Exception {
        long paciente = crearPaciente(medico);
        JsonNode exp = miExpediente(tokenDelPaciente(paciente));

        assertEquals(0, exp.get("alergias").size());
        assertEquals(0, exp.get("consultas").size());
        assertEquals(0, exp.get("recetas").size());
        assertEquals(0, exp.get("proximasCitas").size());
        assertTrue(exp.get("ultimosSignosVitales").isNull());
    }

    // ══════════════════════════════════════════════════════════════════════
    // c2 · el expediente de otro paciente da 403
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("c2: pedir por id el expediente de otro paciente responde 403 en cada endpoint clinico")
    void elExpedienteDeOtroDa403() throws Exception {
        long propio = crearPaciente(medico);
        long otro = crearPaciente(medico);
        crearConsultaSimple(medico, otro);
        String token = tokenDelPaciente(propio);

        for (String ruta : new String[]{
                "/pacientes/" + otro,
                "/pacientes?buscar=Paciente",
                "/consultas?pacienteId=" + otro,
                "/alergias?pacienteId=" + otro,
                "/prescripciones?pacienteId=" + otro,
                "/signos-vitales?pacienteId=" + otro,
                "/signos-vitales/ultima?pacienteId=" + otro,
                "/citas/paciente/" + otro,
                "/antecedentes-patologicos?pacienteId=" + otro}) {
            mockMvc.perform(get(ruta).header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
        }

        // Tampoco el SUYO por esas rutas: no son del portal sino del personal.
        mockMvc.perform(get("/pacientes/{id}", propio).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("c2: /mi ignora cualquier id que se le mande y devuelve siempre el expediente de la sesion")
    void miNoAceptaUnIdAjeno() throws Exception {
        long propio = crearPaciente(medico);
        long otro = crearPaciente(medico);
        registrarAlergia(otro, "Mariscos", "MODERADA");
        String token = tokenDelPaciente(propio);

        JsonNode exp = json.readTree(mockMvc.perform(get("/mi/expediente")
                        .param("pacienteId", String.valueOf(otro))
                        .param("personaId", String.valueOf(otro))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertEquals(propio, exp.get("paciente").get("personaId").asLong());
        assertEquals(0, exp.get("alergias").size(), "las alergias del otro no se cuelan");

        // Una ruta con el id en la URL simplemente no existe.
        mockMvc.perform(get("/mi/expediente/{id}", otro).header("Authorization", bearer(token)))
                .andExpect(r -> assertNotEquals(200, r.getResponse().getStatus()));
    }

    @Test
    @DisplayName("c2: el portal es solo del rol PACIENTE con expediente; sin sesion es 401")
    void elPortalEsDelPaciente() throws Exception {
        for (String token : new String[]{medico, enfermera, tokenDeAdministrador()}) {
            mockMvc.perform(get("/mi/expediente").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/mi/expediente")).andExpect(status().isUnauthorized());

        // Con el rol pero sin fila en `pacientes` no hay expediente que mostrar.
        String correo = correoUnico("portal.sinexpediente");
        Persona persona = personas.saveAndFlush(Persona.builder().nombres("Sin").apellidos("Expediente").build());
        usuarios.saveAndFlush(User.builder()
                .persona(persona).email(correo).password(encoder.encode(CLAVE)).enabled(true)
                .roles(new HashSet<>(Set.of(roles.getReferenceById(RolesEnum.PACIENTE.getId()))))
                .build());
        mockMvc.perform(get("/mi/expediente").header("Authorization", bearer(iniciarSesion(correo))))
                .andExpect(status().isForbidden());
    }

    // ══════════════════════════════════════════════════════════════════════
    // c3 · los terminos tecnicos vienen con su explicacion
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("c3: el expediente trae el glosario, con una explicacion por cada valor cerrado que puede mostrar")
    void glosario() throws Exception {
        JsonNode glosario = miExpediente(tokenDelPaciente(crearPaciente(medico))).get("glosario");

        List<String> claves = StreamSupport.stream(glosario.spliterator(), false)
                .peek(t -> assertFalse(t.get("explicacion").asText().isBlank(), "sin explicacion: " + t))
                .map(t -> t.get("clave").asText())
                .toList();

        for (String clave : List.of("presionArterial", "frecuenciaCardiaca", "frecuenciaRespiratoria",
                "temperatura", "saturacion", "imc", "tipoSangre", "diagnostico", "exploracionFisica",
                "dosis", "viaAdministracion", "frecuencia", "duracion", "cita.AGENDADA")) {
            assertTrue(claves.contains(clave), "falta " + clave + " en el glosario");
        }
        // Si manana se agrega un estado o una severidad, el portal la mostraria
        // sin explicar: esto obliga a escribirla.
        for (SeveridadDeAlergia s : SeveridadDeAlergia.values()) {
            assertTrue(claves.contains("severidad." + s.name()), "falta severidad." + s);
        }
        for (EstadoConsulta e : EstadoConsulta.values()) {
            assertTrue(claves.contains("consulta." + e.name()), "falta consulta." + e);
        }
        for (EstadoPrescripcion e : EstadoPrescripcion.values()) {
            assertTrue(claves.contains("receta." + e.name()), "falta receta." + e);
        }
        assertEquals(claves.size(), Set.copyOf(claves).size(), "claves repetidas en el glosario");
    }

    // ══════════════════════════════════════════════════════════════════════
    // c4 · solo lectura
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("c4: el portal no admite escrituras y el paciente no puede modificar ningun dato clinico")
    void soloLectura() throws Exception {
        long paciente = crearPaciente(medico);
        long consulta = crearConsultaSimple(medico, paciente).get("consultaId").asLong();
        String token = tokenDelPaciente(paciente);

        // /mi no tiene un solo metodo que escriba.
        mockMvc.perform(post("/mi/expediente").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/mi/expediente").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch("/mi/expediente").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/mi/expediente").header("Authorization", bearer(token)))
                .andExpect(status().isMethodNotAllowed());

        // Y los endpoints que si escriben datos clinicos no son para este rol,
        // ni siquiera sobre su propio expediente.
        escribir(token, put("/pacientes/{id}", paciente), """
                {"persona":{"nombres":"Cambiado"},"tipoSangre":"AB-"}""");
        escribir(token, put("/consultas/{id}", consulta), """
                {"diagnostico":"Me lo cambio yo"}""");
        escribir(token, post("/consultas"), """
                {"pacienteId":%d,"motivo":"Autoconsulta","diagnostico":"Sano"}""".formatted(paciente));
        escribir(token, post("/alergias"), """
                {"pacienteId":%d,"sustancia":"Inventada","reaccion":"Ninguna","severidad":"LEVE",
                 "fechaDeteccion":"2020-01-01"}""".formatted(paciente));
        escribir(token, post("/signos-vitales"), """
                {"pacienteId":%d,"pulsoLpm":60}""".formatted(paciente));
        escribir(token, post("/consultas/{id}/anular", consulta), """
                {"motivo":"No me gusto esta consulta"}""");

        JsonNode exp = miExpediente(token);
        assertEquals("O+", exp.get("paciente").get("tipoSangre").asText());
        assertEquals("Control sin hallazgos", exp.get("consultas").get(0).get("diagnostico").asText());
        assertEquals(1, exp.get("consultas").size());
        assertEquals(0, exp.get("alergias").size());
    }

    // ══════════════════════════════════════════════════════════════════════
    // c5 · el expediente en PDF
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("c5: descargar el expediente da un PDF con su informacion y el glosario")
    void expedienteEnPdf() throws Exception {
        long paciente = crearPaciente(medico);
        registrarAlergia(paciente, "Penicilina", "SEVERA");
        long consulta = crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Dolor de garganta","diagnostico":"Faringitis aguda"}
                """.formatted(paciente)).get("consultaId").asLong();
        emitirReceta(consulta);
        String token = tokenDelPaciente(paciente);
        String expediente = miExpediente(token).get("paciente").get("expediente").asText();

        byte[] pdf = mockMvc.perform(get("/mi/expediente/pdf").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"expediente-" + expediente + ".pdf\""))
                .andReturn().getResponse().getContentAsByteArray();

        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
        String texto = textoDe(pdf);
        for (String esperado : List.of("Mi expediente clínico", "Paciente Del Caso", expediente, "Penicilina",
                "ALERGIA SEVERA", "Faringitis aguda", "Amoxicilina", "Oral", "Presión arterial", "Glosario")) {
            assertTrue(texto.contains(esperado), "falta «" + esperado + "» en el PDF:\n" + texto);
        }

        mockMvc.perform(get("/mi/expediente/pdf").header("Authorization", bearer(medico)))
                .andExpect(status().isForbidden());
    }

    // ══════════════════════════════════════════════════════════════════════
    // c6 · cada acceso queda en la bitacora
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("c6: abrir y descargar el expediente quedan en la bitacora, y el administrador la lee")
    void bitacoraDeAccesos() throws Exception {
        long paciente = crearPaciente(medico);
        String correo = correoUnico("portal.bitacora");
        String token = tokenDelPaciente(paciente, correo);

        miExpediente(token);
        miExpediente(token);
        mockMvc.perform(get("/mi/expediente/pdf").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        List<String> tipos = jdbc.queryForList("""
                select tipo from accesos_al_expediente where paciente_id = ?
                order by accedido_en, acceso_id""", String.class, paciente);
        assertEquals(List.of("VISTA", "VISTA", "PDF"), tipos);

        JsonNode accesos = json.readTree(mockMvc.perform(get("/pacientes/{id}/accesos", paciente)
                        .header("Authorization", bearer(tokenDeAdministrador())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(3, accesos.size());
        assertEquals("PDF", accesos.get(0).get("tipo").asText(), "lo mas reciente primero");
        assertEquals(correo, accesos.get(0).get("correoDeSesion").asText());
        assertFalse(accesos.get(0).get("accedidoEn").isNull());

        // Es auditoria: ni el personal clinico ni el propio paciente la leen aqui.
        for (String otro : new String[]{medico, enfermera, token}) {
            mockMvc.perform(get("/pacientes/{id}/accesos", paciente).header("Authorization", bearer(otro)))
                    .andExpect(status().isForbidden());
        }

        // Un acceso rechazado no deja huella.
        mockMvc.perform(get("/mi/expediente").header("Authorization", bearer(medico)))
                .andExpect(status().isForbidden());
        assertEquals(3, jdbc.queryForObject(
                "select count(*) from accesos_al_expediente where paciente_id = ?", Integer.class, paciente));

        mockMvc.perform(get("/pacientes/{id}/accesos", 9_999_999)
                        .header("Authorization", bearer(tokenDeAdministrador())))
                .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════
    // El acceso al portal: cuenta e invitacion desde el expediente
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("acceso: crea la cuenta sin activar con rol PACIENTE y sin sede; reenviar quema la invitacion anterior")
    void darAccesoAlPortal() throws Exception {
        long paciente = crearPaciente(medico);

        JsonNode antes = cuerpo(mockMvc.perform(get("/pacientes/{id}/acceso-portal", paciente)
                .header("Authorization", bearer(enfermera))).andExpect(status().isOk()));
        assertTrue(antes.get("cuenta").isNull());

        // Sin correo en la peticion ni en la ficha no hay a donde invitar.
        darAcceso(enfermera, paciente, "{}").andExpect(status().isBadRequest());

        String correo = correoUnico("portal.acceso");
        JsonNode acceso = cuerpo(darAcceso(enfermera, paciente, "{\"email\":\"%s\"}".formatted(correo))
                .andExpect(status().isOk()));
        assertTrue(acceso.get("invitacionEnviada").asBoolean());
        assertTrue(acceso.get("correoEnviado").asBoolean());
        assertEquals("SIN_ACTIVAR", acceso.get("cuenta").get("estado").asText());
        assertTrue(acceso.get("cuenta").get("conRolPaciente").asBoolean());

        User cuenta = usuarios.findByEmailIgnoreCase(correo).orElseThrow();
        assertFalse(cuenta.isEnabled());
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from clinica_personal where user_id = ?", Integer.class, cuenta.getUserID()),
                "el paciente no opera en ninguna sede");
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from bitacora_de_usuarios where usuario_afectado_id = ? and accion = 'ALTA'""",
                Integer.class, cuenta.getUserID()));

        String primera = invitacionVigenteDe(correo);
        JsonNode reenvio = cuerpo(darAcceso(medico, paciente, "{}").andExpect(status().isOk()));
        assertTrue(reenvio.get("invitacionEnviada").asBoolean());
        String segunda = invitacionVigenteDe(correo);
        assertNotEquals(primera, segunda);
        activar(primera).andExpect(status().isBadRequest());
        activar(segunda).andExpect(status().isNoContent());

        // Ya activa, volver a pedirlo no manda nada ni duplica la cuenta.
        JsonNode activa = cuerpo(darAcceso(medico, paciente, "{}").andExpect(status().isOk()));
        assertFalse(activa.get("invitacionEnviada").asBoolean());
        assertEquals("ACTIVA", activa.get("cuenta").get("estado").asText());

        // Un correo que ya es de otra cuenta no sirve.
        long otro = crearPaciente(medico);
        darAcceso(enfermera, otro, "{\"email\":\"%s\"}".formatted(correo)).andExpect(status().isConflict());

        darAcceso(enfermera, 9_999_999, "{\"email\":\"nadie@correo.sv\"}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("acceso: sin correo en la peticion usa el de contacto del paciente")
    void usaElCorreoDeContacto() throws Exception {
        String correo = correoUnico("portal.contacto");
        String cuerpo = mockMvc.perform(post("/pacientes")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"persona":{"dui":"%s","nombres":"Paciente","apellidos":"Con Correo",
                                            "fechaNacimiento":"1990-05-20","sexo":"F","email":"%s"}}
                                """.formatted(duiUnico(), correo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long paciente = json.readTree(cuerpo).get("personaId").asLong();

        assertEquals(correo, cuerpo(mockMvc.perform(get("/pacientes/{id}/acceso-portal", paciente)
                        .header("Authorization", bearer(medico))).andExpect(status().isOk()))
                .get("correoDeContacto").asText());

        JsonNode acceso = cuerpo(darAcceso(medico, paciente, "{}").andExpect(status().isOk()));
        assertEquals(correo, acceso.get("cuenta").get("email").asText());
    }

    @Test
    @DisplayName("acceso: a una cuenta activa del personal le agrega el rol PACIENTE y conserva los suyos")
    void cuentaExistenteRecibeElRol() throws Exception {
        // La enfermera que tambien se atiende en la clinica: ya tiene cuenta.
        String correo = correoUnico("portal.colega");
        User colega = crearMedicoHabilitado(correo, CLAVE, "Colega", "Que Se Atiende");
        mockMvc.perform(post("/pacientes")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"persona":{"personaId":%d,"dui":"%s","fechaNacimiento":"1985-01-10","sexo":"M"}}
                                """.formatted(colega.getPersona().getPersonaId(), duiUnico())))
                .andExpect(status().isCreated());
        long paciente = colega.getPersona().getPersonaId();

        mockMvc.perform(get("/mi/expediente").header("Authorization", bearer(iniciarSesion(correo))))
                .andExpect(status().isForbidden());

        JsonNode acceso = cuerpo(darAcceso(tokenDeAdministrador(), paciente, "{}").andExpect(status().isOk()));
        assertFalse(acceso.get("invitacionEnviada").asBoolean(), "la cuenta ya estaba activa");
        assertTrue(acceso.get("cuenta").get("conRolPaciente").asBoolean());

        List<String> rolesDeLaCuenta = jdbc.queryForList("""
                select r.name from user_roles ur join role r on r.role_id = ur.role_id where ur.user_id = ?""",
                String.class, colega.getUserID());
        assertTrue(rolesDeLaCuenta.containsAll(List.of("MEDICO", "PACIENTE")), rolesDeLaCuenta.toString());

        // Con la sesion nueva (el token lleva los roles) ya entra a su portal.
        JsonNode exp = miExpediente(iniciarSesion(correo));
        assertEquals(paciente, exp.get("paciente").get("personaId").asLong());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    private String tokenDelPaciente(long pacienteId) throws Exception {
        return tokenDelPaciente(pacienteId, correoUnico("portal.paciente"));
    }

    /** El camino real: acceso desde el expediente, invitacion canjeada, login. */
    private String tokenDelPaciente(long pacienteId, String correo) throws Exception {
        darAcceso(enfermera, pacienteId, "{\"email\":\"%s\"}".formatted(correo)).andExpect(status().isOk());
        activar(invitacionVigenteDe(correo)).andExpect(status().isNoContent());
        return iniciarSesion(correo);
    }

    private JsonNode miExpediente(String token) throws Exception {
        return cuerpo(mockMvc.perform(get("/mi/expediente").header("Authorization", bearer(token)))
                .andExpect(status().isOk()));
    }

    private ResultActions darAcceso(String token, long pacienteId, String cuerpo) throws Exception {
        return mockMvc.perform(post("/pacientes/{id}/acceso-portal", pacienteId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private ResultActions activar(String token) throws Exception {
        return mockMvc.perform(post("/auth/invitacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, CLAVE)));
    }

    private String invitacionVigenteDe(String correo) {
        List<String> tokens = jdbc.queryForList("""
                select i.token from invitaciones i join users u on u.user_id = i.user_id
                where lower(u.email) = lower(?) and i.usada = false
                """, String.class, correo);
        assertEquals(1, tokens.size(), "debe haber exactamente una invitacion vigente");
        return tokens.get(0);
    }

    private void registrarAlergia(long pacienteId, String sustancia, String severidad) throws Exception {
        mockMvc.perform(post("/alergias")
                        .header("Authorization", bearer(enfermera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"sustancia":"%s","reaccion":"Urticaria",
                                 "severidad":"%s","fechaDeteccion":"2020-03-15"}
                                """.formatted(pacienteId, sustancia, severidad)))
                .andExpect(status().isCreated());
    }

    private long emitirReceta(long consultaId) throws Exception {
        Long amoxicilina = jdbc.queryForObject(
                "SELECT medicamento_id FROM medicamentos WHERE nombre_comercial = 'Amoxil' AND presentacion = 'Cápsula'",
                Long.class);
        return cuerpo(mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consultaId":%d,"medicamentos":[{"medicamentoId":%d,"dosis":"1 tableta",
                                 "viaAdministracion":"Oral","frecuencia":"Cada 8 horas","duracion":"7 dias"}]}
                                """.formatted(consultaId, amoxicilina)))
                .andExpect(status().isCreated()))
                .get("prescripcionId").asLong();
    }

    private void anular(String ruta, long id, String motivo) throws Exception {
        mockMvc.perform(post(ruta, id)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"%s\"}".formatted(motivo)))
                .andExpect(status().isOk());
    }

    private int tipoDeCita() throws Exception {
        return cuerpo(mockMvc.perform(post("/tipos-cita")
                        .header("Authorization", bearer(tokenDeAdministrador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Control %d\"}".formatted(siguiente())))
                .andExpect(status().isCreated()))
                .get("tipoCitaId").asInt();
    }

    /** Montaje: agendar por la API exige el horario del medico, que no es lo que se prueba aqui. */
    private void agendarPorDebajo(long pacienteId, int tipo, LocalDateTime fechaHora, String estado) {
        jdbc.update("""
                insert into citas (paciente_persona_id, medico_user_id, tipo_cita_id, fecha_hora, estado,
                                   motivo_cancelacion)
                values (?, ?, ?, ?, ?, ?)""",
                pacienteId, cuentaDelMedico.getUserID(), tipo, fechaHora, estado,
                "CANCELADA".equals(estado) ? "El paciente no puede asistir" : null);
    }

    private void escribir(String token,
                          org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
                          String cuerpo) throws Exception {
        mockMvc.perform(peticion.header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isForbidden());
    }

    private JsonNode porId(JsonNode lista, String campo, long id) {
        for (JsonNode n : lista) {
            if (n.get(campo).asLong() == id) return n;
        }
        throw new AssertionError("no esta " + campo + "=" + id + " en " + lista);
    }

    private JsonNode cuerpo(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private static String textoDe(byte[] pdf) throws Exception {
        var lector = new com.lowagie.text.pdf.PdfReader(pdf);
        var extractor = new com.lowagie.text.pdf.parser.PdfTextExtractor(lector);
        StringBuilder texto = new StringBuilder();
        for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
            texto.append(extractor.getTextFromPage(pagina)).append('\n');
        }
        return texto.toString();
    }
}
