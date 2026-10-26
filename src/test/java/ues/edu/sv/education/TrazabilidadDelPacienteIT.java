package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-07 criterio 4 y HU-09 criterio 4: el paciente guarda quien lo registro,
 * en que clinica, y quien lo modifico por ultima vez y cuando.
 */
class TrazabilidadDelPacienteIT extends PruebaClinica {

    private JsonNode registrar(String token, Integer clinicaId) throws Exception {
        String sede = clinicaId == null ? "" : ",\"clinicaId\":" + clinicaId;
        String cuerpo = mockMvc.perform(post("/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"persona":{"dui":"%s","nombres":"Paciente","apellidos":"Trazado %d",
                                            "fechaNacimiento":"1990-05-20","sexo":"F"},
                                 "tipoSangre":"O+"%s}
                                """.formatted(duiUnico(), siguiente(), sede)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private JsonNode ficha(String token, long id) throws Exception {
        return json.readTree(mockMvc.perform(get("/pacientes/{id}", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** Da de alta una clinica por la API (la base de pruebas no trae ninguna) y devuelve su id. */
    private int unaClinica(String token) throws Exception {
        String cuerpo = mockMvc.perform(post("/clinics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Clinica Trazada %1$d","latitud":13.99,"longitud":-89.55,
                                 "departamento":"Santa Ana","municipio":"Municipio Trazado %1$d",
                                 "direccion":"Calle Principal","telefono":"2440-0000",
                                 "horario":"Lunes a viernes, 8:00 a 16:00"}
                                """.formatted(siguiente())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("clinicaId").asInt();
    }

    @Test
    @DisplayName("al registrar, el paciente guarda quien lo registro y la clinica indicada")
    void guardaQuienYDondeLoRegistro() throws Exception {
        String token = tokenDeMedico();
        int clinica = unaClinica(token);

        JsonNode creado = registrar(token, clinica);
        JsonNode ficha = ficha(token, creado.get("personaId").asLong());

        assertTrue(ficha.get("registradoPor").asText().startsWith("clinico.medico."),
                "quien registro sale del token: " + ficha.get("registradoPor"));
        assertEquals(clinica, ficha.get("clinicaRegistroId").asInt());
        assertFalse(ficha.get("clinicaRegistro").asText().isBlank());
        assertTrue(ficha.get("modificadoPor").isNull(), "recien creado, nadie lo ha modificado");
        assertTrue(ficha.get("modificadoEn").isNull());
    }

    @Test
    @DisplayName("sin clinica indicada queda el usuario y la clinica en null")
    void sinClinicaQuedaNull() throws Exception {
        String token = tokenDeMedico();

        JsonNode ficha = ficha(token, registrar(token, null).get("personaId").asLong());

        assertFalse(ficha.get("registradoPor").isNull());
        assertTrue(ficha.get("clinicaRegistroId").isNull());
        assertTrue(ficha.get("clinicaRegistro").isNull());
    }

    @Test
    @DisplayName("una clinica inexistente es 404 y no crea al paciente")
    void clinicaInexistente() throws Exception {
        String token = tokenDeMedico();

        mockMvc.perform(post("/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"persona":{"dui":"%s","nombres":"Paciente","apellidos":"Sin Sede",
                                            "fechaNacimiento":"1990-05-20","sexo":"F"},
                                 "tipoSangre":"O+","clinicaId":99999999}
                                """.formatted(duiUnico())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("al modificar, queda quien lo hizo y cuando; el registro original no cambia")
    void guardaQuienLoModificoPorUltimaVez() throws Exception {
        String registrador = tokenDeMedico();
        long id = registrar(registrador, unaClinica(registrador)).get("personaId").asLong();
        String registradoPor = ficha(registrador, id).get("registradoPor").asText();

        String otro = tokenDeMedico();
        mockMvc.perform(put("/pacientes/{id}", id)
                        .header("Authorization", "Bearer " + otro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"persona":{"telefono":"7000-0000"},"tipoSangre":"A+"}
                                """))
                .andExpect(status().isOk());

        JsonNode ficha = ficha(registrador, id);
        assertNotEquals(registradoPor, ficha.get("modificadoPor").asText(),
                "lo modifico otro medico, y eso es lo que debe constar");
        assertFalse(ficha.get("modificadoEn").isNull());
        assertEquals(registradoPor, ficha.get("registradoPor").asText(),
                "modificar no reescribe quien lo registro");
    }

    @Test
    @DisplayName("dar de baja tambien cuenta como modificacion")
    void laBajaTambienSeRegistra() throws Exception {
        String registrador = tokenDeMedico();
        long id = registrar(registrador, null).get("personaId").asLong();
        String admin = tokenDeAdministrador();

        mockMvc.perform(patch("/pacientes/{id}/estado", id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"INACTIVO\"}"))
                .andExpect(status().isOk());

        JsonNode ficha = ficha(admin, id);
        assertTrue(ficha.get("modificadoPor").asText().startsWith("clinico.admin."),
                ficha.get("modificadoPor").toString());
        assertFalse(ficha.get("modificadoEn").isNull());
    }
}
