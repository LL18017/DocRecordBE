package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import ues.edu.sv.education.model.enums.RolesEnum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-02 (DRS-77) · Alta de medicos con especialidad y numero de junta, hecha
 * por el administrador.
 *
 * Reemplaza a RegistroDeMedicoIT. El registro publico (/auth/register) se
 * retiro por decision del PO: cualquiera podia darse de alta como medico y
 * leer todos los expedientes. Ahora el administrador crea la cuenta (POST
 * /user, que manda la confirmacion por correo) y le da el rol MEDICO con su
 * especialidad, su sede y su numero de junta (POST /user/{id}/role/{MEDICO}).
 *
 * Criterio 1: la junta es obligatoria y tiene formato. Criterio 2: un correo
 * ya registrado responde 409 (AltaDeUsuarioIT lo cubre para POST /user). El
 * correo de confirmacion lo cubren CorreoDeConfirmacionIT y
 * RegistroSinCorreoIT.
 */
class AltaDeMedicoIT extends PruebaClinica {

    @Autowired private JdbcTemplate jdbc;

    private String admin;
    private int sede;

    @BeforeEach
    void prepararAdministrador() throws Exception {
        admin = tokenDeAdministrador();
        sede = crearSede(admin);
    }

    /** Crea la cuenta como administrador y devuelve su id. */
    private int crearCuenta(String correo) throws Exception {
        String cuerpo = mockMvc.perform(post("/user")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","userName":"Lucia Mejia","password":"%s"}
                                """.formatted(correo, CLAVE)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("userId").asInt();
    }

    private ResultActions darRolDeMedico(int userId, String registroJunta) throws Exception {
        var peticion = post("/user/{id}/role/{rol}", userId, RolesEnum.MEDICO.getId())
                .header("Authorization", bearer(admin))
                .param("especialidadId", "1")
                .param("clinicaId", String.valueOf(sede));
        if (registroJunta != null) peticion.param("registroJunta", registroJunta);
        return mockMvc.perform(peticion);
    }

    private String juntaGuardada(int userId) {
        return jdbc.queryForObject("""
                select m.registro_junta from medicos m
                join users u on u.persona_id = m.persona_id
                where u.user_id = ?
                """, String.class, userId);
    }

    private long rolesDe(int userId) {
        return jdbc.queryForObject("select count(*) from user_roles where user_id = ?", Long.class, userId);
    }

    @Test
    @DisplayName("HU-02: el registro publico ya no existe")
    void noHayRegistroPublico() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Lucia","apellidos":"Mejia","email":"%s","password":"%s",
                                 "especialidadId":1,"registroJunta":"%s"}
                                """.formatted(correoUnico("registro.publico"), CLAVE, juntaUnica())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("c1: con especialidad, sede y junta validas el medico queda dado de alta y la junta se guarda normalizada")
    void c1_conJuntaValidaQuedaElMedico() throws Exception {
        int userId = crearCuenta(correoUnico("alta.medico"));
        String junta = juntaUnica().toLowerCase();

        JsonNode respuesta = json.readTree(darRolDeMedico(userId, "  " + junta + " ")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertTrue(respuesta.get("roles").toString().contains("MEDICO"), respuesta.toString());
        // Se guarda normalizada: sin esto, "jvpm-1" y "JVPM-1" serian dos medicos.
        assertEquals(junta.toUpperCase(), juntaGuardada(userId));
    }

    @Test
    @DisplayName("c1: sin numero de junta no se da el rol de medico, y la cuenta queda sin rol")
    void c1_sinJuntaNoSeDaElRol() throws Exception {
        int userId = crearCuenta(correoUnico("alta.sinjunta"));

        String cuerpo = darRolDeMedico(userId, null)
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertTrue(cuerpo.contains("número de junta de vigilancia"), cuerpo);
        assertEquals(0, rolesDe(userId), "un rechazo no debe dejar el rol a medias");
    }

    @Test
    @DisplayName("c1: una junta con formato invalido se rechaza con 400; formatos legitimos distintos pasan")
    void c1_unaJuntaConFormatoInvalidoSeRechaza() throws Exception {
        int userId = crearCuenta(correoUnico("alta.formato"));

        // Muy corta, sin ningun numero, con espacios o simbolos, muy larga.
        for (String invalida : new String[]{"A1", "SIN-NUMERO", "JVPM 1234", "JVPM#1234",
                "JVPM-12345678901234567"}) {
            darRolDeMedico(userId, invalida).andExpect(status().isBadRequest());
        }
        assertEquals(0, rolesDe(userId));

        darRolDeMedico(crearCuenta(correoUnico("alta.formato.a")), "12345" + siguiente())
                .andExpect(status().isOk());
        darRolDeMedico(crearCuenta(correoUnico("alta.formato.b")), "JVPM-ENF-" + siguiente())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("la junta de otro medico se rechaza con 409, aunque cambie la caja")
    void laJuntaNoSeRepiteEntreMedicos() throws Exception {
        String junta = juntaUnica();
        darRolDeMedico(crearCuenta(correoUnico("alta.junta.uno")), junta).andExpect(status().isOk());

        int otro = crearCuenta(correoUnico("alta.junta.dos"));
        String cuerpo = darRolDeMedico(otro, junta.toLowerCase())
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        assertTrue(json.readTree(cuerpo).get("message").asText().contains("junta"), cuerpo);
        assertEquals(0, rolesDe(otro));
    }

    @Test
    @DisplayName("un medico dado de alta antes de exigir la junta la completa volviendo a darle el rol con ella")
    void unMedicoSinJuntaLaCompleta() throws Exception {
        int userId = crearCuenta(correoUnico("alta.completar"));
        darRolDeMedico(userId, juntaUnica()).andExpect(status().isOk());
        jdbc.update("update medicos set registro_junta = null where persona_id = "
                + "(select persona_id from users where user_id = ?)", userId);

        String nueva = juntaUnica();
        darRolDeMedico(userId, nueva).andExpect(status().isOk());
        assertEquals(nueva, juntaGuardada(userId));
    }

    @Test
    @DisplayName("solo un administrador da de alta medicos")
    void soloElAdministradorDaDeAltaMedicos() throws Exception {
        int userId = crearCuenta(correoUnico("alta.permiso"));

        for (String token : new String[]{tokenDeMedico(), tokenDeEnfermera()}) {
            mockMvc.perform(post("/user/{id}/role/{rol}", userId, RolesEnum.MEDICO.getId())
                            .header("Authorization", bearer(token))
                            .param("especialidadId", "1")
                            .param("clinicaId", String.valueOf(sede))
                            .param("registroJunta", juntaUnica()))
                    .andExpect(status().isForbidden());
        }
        assertEquals(0, rolesDe(userId));
    }
}
