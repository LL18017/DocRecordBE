package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import ues.edu.sv.education.model.entity.Persona;
import ues.edu.sv.education.model.entity.User;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-02 (DRS-77) · Ficha del medico y su acceso al sistema.
 *
 * El medico existe primero como ficha -especialidad y numero de junta, sin
 * cuenta- y despues, desde la ficha, el administrador le da acceso: se crea la
 * cuenta y se le manda una invitacion para que elija su contrasena. Igual que
 * el paciente, cuya cuenta se activa sobre un expediente que ya existe.
 *
 * Reemplaza a AltaDeMedicoIT, que probaba la ficha creada al dar el rol en
 * Usuarios y Roles; eso ya no existe (ver FichaDeMedicoAlAsignarRolIT).
 */
class FichaDeMedicoIT extends PruebaClinica {

    @Autowired private JdbcTemplate jdbc;

    private String admin;
    private int sede;

    @BeforeEach
    void prepararAdministrador() throws Exception {
        admin = tokenDeAdministrador();
        sede = crearSede(admin);
    }

    // ══════════════════════════════════════════════════════════════════════
    // La ficha
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("c1: la ficha se registra con especialidad y junta normalizada, y sin cuenta")
    void laFichaSeRegistraSinCuenta() throws Exception {
        String junta = juntaUnica().toLowerCase();

        JsonNode ficha = cuerpo(crearFicha(fichaJson("Lucía", "Mejía", "  " + junta + " ", "77770000"))
                .andExpect(status().isCreated()));

        assertEquals(junta.toUpperCase(), ficha.get("registroJunta").asText());
        assertEquals("7777-0000", ficha.get("telefono").asText());
        assertTrue(ficha.get("activo").asBoolean());
        assertTrue(ficha.get("cuenta").isNull(), "registrar la ficha no crea cuenta");

        JsonNode lista = cuerpo(mockMvc.perform(get("/medicos").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()));
        assertTrue(lista.toString().contains(junta.toUpperCase()));
    }

    @Test
    @DisplayName("c1: sin junta, con formato invalido o con la junta de otro medico no se registra")
    void laJuntaSeValida() throws Exception {
        crearFicha("""
                {"nombres":"Sin","apellidos":"Junta","especialidadId":1}
                """).andExpect(status().isBadRequest());

        for (String invalida : new String[]{"A1", "SIN-NUMERO", "JVPM 1234", "JVPM#1234", "JVPM-12345678901234567"}) {
            crearFicha(fichaJson("Mal", "Formato", invalida, null)).andExpect(status().isBadRequest());
        }

        String junta = juntaUnica();
        crearFicha(fichaJson("Uno", "Primero", junta, null)).andExpect(status().isCreated());
        String cuerpo = crearFicha(fichaJson("Dos", "Segundo", junta.toLowerCase(), null))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(cuerpo.contains("junta"), cuerpo);

        crearFicha("""
                {"nombres":"Sin","apellidos":"Especialidad","especialidadId":99999,"registroJunta":"%s"}
                """.formatted(juntaUnica())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("la ficha se corrige; cambiar a la junta de otro medico da 409")
    void laFichaSeCorrige() throws Exception {
        long id = idDe(crearFicha(fichaJson("Ana", "Antes", juntaUnica(), null)).andExpect(status().isCreated()));
        String ajena = juntaUnica();
        crearFicha(fichaJson("Otro", "Medico", ajena, null)).andExpect(status().isCreated());

        String nueva = juntaUnica();
        JsonNode corregida = cuerpo(mockMvc.perform(put("/medicos/{id}", id)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fichaJson("Ana", "Despues", nueva, null)))
                .andExpect(status().isOk()));
        assertEquals("Despues", corregida.get("apellidos").asText());
        assertEquals(nueva, corregida.get("registroJunta").asText());

        mockMvc.perform(put("/medicos/{id}", id)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fichaJson("Ana", "Despues", ajena, null)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("solo el administrador ve y registra fichas de medico")
    void soloElAdministrador() throws Exception {
        for (String token : new String[]{tokenDeMedico(), tokenDeEnfermera()}) {
            mockMvc.perform(get("/medicos").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/medicos")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fichaJson("Intruso", "Medico", juntaUnica(), null)))
                    .andExpect(status().isForbidden());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // El acceso: cuenta e invitacion
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("dar acceso crea la cuenta sin activar y manda la invitacion; canjearla la activa y deja entrar")
    void darAccesoEInvitar() throws Exception {
        long id = idDe(crearFicha(fichaJson("Lucía", "Mejía", juntaUnica(), null)).andExpect(status().isCreated()));
        String correo = correoUnico("ficha.acceso");

        // Sin correo o sin sede no hay acceso.
        darAcceso(id, "{\"clinicaId\":%d}".formatted(sede)).andExpect(status().isBadRequest());
        darAcceso(id, "{\"email\":\"%s\"}".formatted(correo)).andExpect(status().isBadRequest());

        clearInvocations(correoDePruebas);
        JsonNode acceso = cuerpo(darAcceso(id, "{\"email\":\"%s\",\"clinicaId\":%d}".formatted(correo, sede))
                .andExpect(status().isOk()));
        assertTrue(acceso.get("invitacionEnviada").asBoolean());
        assertTrue(acceso.get("correoEnviado").asBoolean());
        JsonNode cuenta = acceso.get("medico").get("cuenta");
        assertEquals(correo, cuenta.get("email").asText());
        assertEquals("SIN_ACTIVAR", cuenta.get("estado").asText());
        assertTrue(cuenta.get("conRolMedico").asBoolean());
        assertEquals(1, cuenta.get("sedes").size());

        // El correo lleva el enlace a la pantalla de activar del frontend.
        ArgumentCaptor<MimeMessage> mensajes = ArgumentCaptor.forClass(MimeMessage.class);
        verify(correoDePruebas, atLeastOnce()).send(mensajes.capture());
        String texto = CorreosDePrueba.parteDeTexto(mensajes.getValue());
        String token = invitacionVigenteDe(correo);
        assertTrue(texto.contains("/activar?token=" + token), texto);

        // Antes de canjearla no se puede entrar: nadie conoce la contrasena.
        assertFalse(puedeEntrar(correo, CLAVE));

        // Una contrasena debil no la canjea.
        activar(token, "debil").andExpect(status().isBadRequest());

        activar(token, CLAVE).andExpect(status().isNoContent());
        assertTrue(puedeEntrar(correo, CLAVE), "con la invitacion canjeada la cuenta entra");

        // La invitacion sirve una sola vez.
        activar(token, "OtraClave2026!").andExpect(status().isBadRequest());
        assertEquals("ACTIVA", cuerpo(mockMvc.perform(get("/medicos/{id}", id).header("Authorization", bearer(admin))))
                .get("cuenta").get("estado").asText());
    }

    @Test
    @DisplayName("darle acceso otra vez a una cuenta sin activar manda una invitacion nueva y la anterior deja de servir")
    void reenviarLaInvitacion() throws Exception {
        long id = idDe(crearFicha(fichaJson("Mario", "Reenvio", juntaUnica(), null)).andExpect(status().isCreated()));
        String correo = correoUnico("ficha.reenvio");
        darAcceso(id, "{\"email\":\"%s\",\"clinicaId\":%d}".formatted(correo, sede)).andExpect(status().isOk());
        String primera = invitacionVigenteDe(correo);

        JsonNode reenvio = cuerpo(darAcceso(id, "{}").andExpect(status().isOk()));
        assertTrue(reenvio.get("invitacionEnviada").asBoolean());
        String segunda = invitacionVigenteDe(correo);
        assertFalse(primera.equals(segunda));

        activar(primera, CLAVE).andExpect(status().isBadRequest());
        activar(segunda, CLAVE).andExpect(status().isNoContent());

        // Ya activa, volver a darle acceso no manda nada.
        assertFalse(cuerpo(darAcceso(id, "{}").andExpect(status().isOk())).get("invitacionEnviada").asBoolean());
    }

    @Test
    @DisplayName("un correo que ya es de otra cuenta no se puede usar para el acceso")
    void correoRepetido() throws Exception {
        long id = idDe(crearFicha(fichaJson("Correo", "Repetido", juntaUnica(), null)).andExpect(status().isCreated()));
        String correo = correoUnico("ficha.repetido");
        crearMedicoHabilitado(correo, CLAVE, "Ya", "Existe");

        darAcceso(id, "{\"email\":\"%s\",\"clinicaId\":%d}".formatted(correo, sede))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("la baja le quita el rol MEDICO a la cuenta; dada de baja no se le da acceso; reactivada si")
    void bajaYReactivacion() throws Exception {
        long id = idDe(crearFicha(fichaJson("Baja", "De Ficha", juntaUnica(), null)).andExpect(status().isCreated()));
        String correo = correoUnico("ficha.baja");
        darAcceso(id, "{\"email\":\"%s\",\"clinicaId\":%d}".formatted(correo, sede)).andExpect(status().isOk());

        JsonNode baja = cuerpo(cambiarEstado(id, false).andExpect(status().isOk()));
        assertFalse(baja.get("activo").asBoolean());
        assertFalse(baja.get("cuenta").get("conRolMedico").asBoolean(), "la baja quita el rol MEDICO");

        darAcceso(id, "{}").andExpect(status().isConflict());

        cambiarEstado(id, true).andExpect(status().isOk());
        JsonNode acceso = cuerpo(darAcceso(id, "{}").andExpect(status().isOk()));
        assertTrue(acceso.get("medico").get("cuenta").get("conRolMedico").asBoolean());
    }

    @Test
    @DisplayName("la ficha se puede colgar de una cuenta que ya existe, y el acceso solo le da el rol")
    void fichaSobreUnaCuentaExistente() throws Exception {
        User cuenta = cuentaSinRoles();

        JsonNode ficha = cuerpo(crearFicha("""
                {"nombres":"Ignorado","apellidos":"Ignorado","especialidadId":1,"registroJunta":"%s","userId":%d}
                """.formatted(juntaUnica(), cuenta.getUserID())).andExpect(status().isCreated()));
        assertEquals(cuenta.getPersona().getPersonaId(), ficha.get("personaId").asLong());
        assertEquals("Cuenta", ficha.get("nombres").asText(), "los nombres salen de la persona de la cuenta");

        // Una segunda ficha para la misma persona no.
        crearFicha("""
                {"nombres":"X","apellidos":"Y","especialidadId":1,"registroJunta":"%s","userId":%d}
                """.formatted(juntaUnica(), cuenta.getUserID())).andExpect(status().isConflict());

        JsonNode acceso = cuerpo(darAcceso(ficha.get("personaId").asLong(), "{\"clinicaId\":%d}".formatted(sede))
                .andExpect(status().isOk()));
        assertFalse(acceso.get("invitacionEnviada").asBoolean(), "la cuenta ya estaba activa");
        assertTrue(acceso.get("medico").get("cuenta").get("conRolMedico").asBoolean());
    }

    @Test
    @DisplayName("el canje de la invitacion es publico y un token inventado da 400")
    void tokenInventado() throws Exception {
        activar("no-existe-" + siguiente(), CLAVE).andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    private String fichaJson(String nombres, String apellidos, String junta, String telefono) {
        String tel = telefono == null ? "" : ",\"telefono\":\"" + telefono + "\"";
        return """
                {"nombres":"%s","apellidos":"%s","especialidadId":1,"registroJunta":"%s"%s}
                """.formatted(nombres, apellidos, junta, tel);
    }

    private ResultActions crearFicha(String cuerpo) throws Exception {
        return mockMvc.perform(post("/medicos")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private ResultActions darAcceso(long personaId, String cuerpo) throws Exception {
        return mockMvc.perform(post("/medicos/{id}/acceso", personaId)
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private ResultActions cambiarEstado(long personaId, boolean activo) throws Exception {
        return mockMvc.perform(patch("/medicos/{id}/estado", personaId)
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\":%s}".formatted(activo)));
    }

    private ResultActions activar(String token, String clave) throws Exception {
        return mockMvc.perform(post("/auth/invitacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, clave)));
    }

    private String invitacionVigenteDe(String correo) {
        List<String> tokens = jdbc.queryForList("""
                select i.token from invitaciones i join users u on u.user_id = i.user_id
                where lower(u.email) = lower(?) and i.usada = false
                """, String.class, correo);
        assertEquals(1, tokens.size(), "debe haber exactamente una invitacion vigente");
        return tokens.get(0);
    }

    private boolean puedeEntrar(String correo, String clave) throws Exception {
        int estado = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(correo, clave)))
                .andReturn().getResponse().getStatus();
        return estado / 100 == 2;
    }

    /** Una cuenta habilitada sin roles, como la de alguien que todavia no ejerce. */
    private User cuentaSinRoles() {
        Persona persona = personas.saveAndFlush(Persona.builder().nombres("Cuenta").apellidos("Existente").build());
        return usuarios.saveAndFlush(User.builder()
                .persona(persona)
                .email(correoUnico("ficha.existente"))
                .password(encoder.encode(CLAVE))
                .enabled(true)
                .roles(new HashSet<>(Set.<ues.edu.sv.education.model.entity.Role>of()))
                .build());
    }

    private long idDe(ResultActions resultado) throws Exception {
        return cuerpo(resultado).get("personaId").asLong();
    }

    private JsonNode cuerpo(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }
}
