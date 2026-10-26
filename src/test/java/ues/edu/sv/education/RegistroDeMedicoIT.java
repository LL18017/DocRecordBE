package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import ues.edu.sv.education.model.entity.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-02 (DRS-77) · Registro de médicos: número de junta de vigilancia.
 *
 * Criterio 1: el registro pide nombre, correo, especialidad y número de junta
 * válidos, y responde 201 con rol MEDICO. Criterio 2: un correo ya registrado
 * responde 409 «Este correo ya está registrado», sin duplicar el usuario.
 * Los criterios 3 y 4 (correo de verificación y bloqueo de consultas) los
 * cubren CorreoDeConfirmacionIT y las pruebas de consulta.
 */
class RegistroDeMedicoIT extends PruebaClinica {

    @Autowired private JdbcTemplate jdbc;

    private ResultActions registrar(String correo, String registroJunta) throws Exception {
        String junta = registroJunta == null ? "" : ",\"registroJunta\":\"" + registroJunta + "\"";
        return mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombres":"Lucia","apellidos":"Mejia","email":"%s","password":"%s",
                         "especialidadId":1%s}
                        """.formatted(correo, CLAVE, junta)));
    }

    private String juntaGuardada(String correo) {
        return jdbc.queryForObject("""
                select m.registro_junta from medicos m
                join users u on u.persona_id = m.persona_id
                where lower(u.email) = lower(?)
                """, String.class, correo);
    }

    private long cuentasCon(String correo) {
        return jdbc.queryForObject(
                "select count(*) from users where lower(email) = lower(?)", Long.class, correo);
    }

    @Test
    @DisplayName("c1: con nombre, correo, especialidad y junta validos responde 201, rol MEDICO y guarda la junta")
    void c1_conJuntaValidaSeCreaLaCuentaYSeGuardaLaJunta() throws Exception {
        String correo = correoUnico("registro.junta");
        String junta = juntaUnica().toLowerCase();

        String cuerpo = registrar(correo, junta)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode respuesta = json.readTree(cuerpo);
        assertEquals("MEDICO", respuesta.get("roles").get(0).asText());
        // Se guarda normalizada: sin esto, "jvpm-1" y "JVPM-1" serian dos medicos.
        assertEquals(junta.toUpperCase(), juntaGuardada(correo));
    }

    @Test
    @DisplayName("c1: sin numero de junta el registro se rechaza con 400 y no crea nada")
    void c1_sinJuntaSeRechaza() throws Exception {
        String correo = correoUnico("registro.sinjunta");

        String cuerpo = registrar(correo, null)
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertTrue(cuerpo.contains("El número de junta de vigilancia es obligatorio"), cuerpo);
        assertEquals(0, cuentasCon(correo), "un registro rechazado no debe dejar usuario");
    }

    @Test
    @DisplayName("c1: una junta en blanco o con formato invalido se rechaza con 400")
    void c1_unaJuntaConFormatoInvalidoSeRechaza() throws Exception {
        String correo = correoUnico("registro.formato");

        // En blanco, muy corta, sin ningun numero, con espacios o simbolos, muy larga.
        for (String invalida : new String[]{"   ", "A1", "SIN-NUMERO", "JVPM 1234", "JVPM#1234",
                "JVPM-12345678901234567"}) {
            registrar(correo, invalida).andExpect(status().isBadRequest());
        }
        assertEquals(0, cuentasCon(correo));

        // Y formatos legitimos distintos entre si si pasan.
        registrar(correoUnico("registro.formato.a"), "12345" + siguiente()).andExpect(status().isCreated());
        registrar(correoUnico("registro.formato.b"), "JVPM-ENF-" + siguiente()).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("c2: un correo ya registrado responde 409 «Este correo ya está registrado» y no duplica el usuario")
    void c2_unCorreoYaRegistradoResponde409SinDuplicar() throws Exception {
        String correo = correoUnico("registro.duplicado");
        registrar(correo, juntaUnica()).andExpect(status().isCreated());

        // Mientras no confirme, registrarse otra vez sobreescribe la cuenta (es
        // el reenvio del enlace). El 409 es para una cuenta ya confirmada.
        User usuario = usuarios.findByEmailIgnoreCase(correo).orElseThrow();
        usuario.setEnabled(true);
        usuarios.saveAndFlush(usuario);

        String cuerpo = registrar(correo.toUpperCase(), juntaUnica())
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        assertEquals("Este correo ya está registrado", json.readTree(cuerpo).get("message").asText());
        assertEquals(1, cuentasCon(correo));
    }

    @Test
    @DisplayName("la junta de otro medico se rechaza con 409; reintentar el propio registro sin confirmar si se permite")
    void laJuntaNoSeRepiteEntreMedicos() throws Exception {
        String junta = juntaUnica();
        String primero = correoUnico("registro.junta.uno");
        registrar(primero, junta).andExpect(status().isCreated());

        // Otra persona con la MISMA junta (aunque cambie la caja).
        String otro = correoUnico("registro.junta.dos");
        String cuerpo = registrar(otro, junta.toLowerCase())
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(json.readTree(cuerpo).get("message").asText().contains("junta"), cuerpo);
        assertEquals(0, cuentasCon(otro));

        // La misma persona reintenta su registro sin confirmar: no choca consigo.
        registrar(primero, junta).andExpect(status().isCreated());
        assertEquals(1, cuentasCon(primero));
    }

    @Test
    @DisplayName("reintentar un registro sin confirmar con otra junta la reemplaza")
    void reintentarConOtraJuntaLaReemplaza() throws Exception {
        String correo = correoUnico("registro.reintento");
        registrar(correo, juntaUnica()).andExpect(status().isCreated());

        String corregida = juntaUnica();
        registrar(correo, corregida).andExpect(status().isCreated());

        assertEquals(corregida, juntaGuardada(correo));
    }
}
