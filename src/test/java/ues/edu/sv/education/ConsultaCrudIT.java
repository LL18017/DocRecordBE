package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consultas medicas (epica E6) a traves de la API real.
 *
 * Lo que se prueba aqui no es "que el CRUD funcione" sino las tres reglas que
 * convierten esto en un expediente y no en una libreta: que el medico sea el
 * autenticado, que el diagnostico solo lo escriba un medico, y que el historial
 * de un paciente sea completo y este ordenado.
 */
class ConsultaCrudIT extends PruebaClinica {

    private String medico;
    private long paciente;

    @BeforeEach
    void prepararMedicoYPaciente() throws Exception {
        medico = tokenDeMedico();
        paciente = crearPaciente(medico);
    }

    // ══════════════════════════════════════════════════════════════════════
    // El medico sale del token
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("el medico de la consulta es el autenticado, no el que venga en el cuerpo")
    void elMedicoEsElAutenticadoYNoElDelCuerpo() throws Exception {
        // Es el fallo que ya ocurrio en este backend con ClinicaService: recibir
        // la identidad en el request. En un expediente clinico significaria
        // poder atribuirle una atencion a otro medico.
        String otroMedico = tokenDeMedico();
        long idDelOtroMedico = crearConsultaSimple(otroMedico, paciente)
                .get("medico").get("personaId").asLong();

        JsonNode creada = crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Control","medicoId":%d,
                 "medico":{"personaId":%d}}
                """.formatted(paciente, idDelOtroMedico, idDelOtroMedico));

        long firmante = creada.get("medico").get("personaId").asLong();

        assertNotEquals(idDelOtroMedico, firmante,
                "la consulta quedo atribuida al medico que venia en el cuerpo");

        // Y ademas es efectivamente quien pidio: se comprueba contra una
        // consulta suya sin manipular.
        long idDelMedicoAutenticado = crearConsultaSimple(medico, paciente)
                .get("medico").get("personaId").asLong();
        assertEquals(idDelMedicoAutenticado, firmante);
    }

    @Test
    @DisplayName("un administrador que no ejerce no puede registrar una consulta")
    void unAdministradorQueNoEjerceNoPuedeRegistrarConsultas() throws Exception {
        // Pasa el @PreAuthorize (es ADMIN) y aun asi debe detenerse: una
        // consulta necesita un medico de verdad al que atribuirsela, y este
        // usuario no tiene fila en `medicos`.
        String admin = tokenDeAdminQueNoEjerce();

        mockMvc.perform(post("/consultas")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d,\"motivo\":\"Control\"}".formatted(paciente)))
                .andExpect(status().isForbidden());
    }

    // ══════════════════════════════════════════════════════════════════════
    // El diagnostico es exclusivo del medico
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("una enfermera no puede escribir el diagnostico de una consulta")
    void unaEnfermeraNoPuedeEscribirDiagnostico() throws Exception {
        String enfermera = tokenDeEnfermera();
        long consultaId = crearConsultaSimple(medico, paciente).get("consultaId").asLong();

        mockMvc.perform(post("/consultas")
                        .header("Authorization", bearer(enfermera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"motivo":"Fiebre","diagnostico":"Dengue"}
                                """.formatted(paciente)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/consultas/{id}", consultaId)
                        .header("Authorization", bearer(enfermera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diagnostico\":\"Dengue\"}"))
                .andExpect(status().isForbidden());

        // Lo que de verdad importa: que no haya quedado escrito. Un 403 que
        // igual guarda el dato no protege nada.
        JsonNode consulta = leerConsulta(medico, consultaId);
        assertTrue(consulta.get("diagnostico").isNull(),
                "la enfermera recibio 403 pero el diagnostico quedo escrito");
        assertEquals("PENDIENTE", consulta.get("estado").asText());
    }

    @Test
    @DisplayName("un administrador que no ejerce puede corregir el motivo pero no diagnosticar")
    void unAdministradorNoPuedeDiagnosticarAunqueEntreAlEndpoint() throws Exception {
        // Esta es la prueba que le da dientes a la regla. La enfermera se queda
        // fuera ya en la anotacion del controller; el administrador la
        // atraviesa, asi que si el 403 llega es porque el servicio -- no la
        // anotacion -- distingue administrar de diagnosticar.
        String admin = tokenDeAdminQueNoEjerce();
        long consultaId = crearConsultaSimple(medico, paciente).get("consultaId").asLong();

        // Puede lo administrativo.
        mockMvc.perform(put("/consultas/{id}", consultaId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Motivo corregido por recepcion\"}"))
                .andExpect(status().isOk());

        // No puede lo clinico.
        mockMvc.perform(put("/consultas/{id}", consultaId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"motivo":"Otro motivo mas","diagnostico":"Faringitis aguda"}
                                """))
                .andExpect(status().isForbidden());

        JsonNode consulta = leerConsulta(medico, consultaId);
        assertTrue(consulta.get("diagnostico").isNull(), "el administrador escribio un diagnostico");
        assertEquals("Motivo corregido por recepcion", consulta.get("motivo").asText(),
                "la peticion rechazada no debio guardar tampoco el motivo que la acompanaba");
    }

    @Test
    @DisplayName("el medico si puede diagnosticar, y eso cierra la consulta")
    void elMedicoSiPuedeDiagnosticar() throws Exception {
        // Contrapeso: sin esta prueba, negar el diagnostico a todo el mundo
        // pasaria las dos anteriores.
        long consultaId = crearConsultaSimple(medico, paciente).get("consultaId").asLong();

        String cuerpo = mockMvc.perform(put("/consultas/{id}", consultaId)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diagnostico\":\"Faringitis aguda\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode consulta = json.readTree(cuerpo);
        assertEquals("Faringitis aguda", consulta.get("diagnostico").asText());
        assertEquals("FINALIZADA", consulta.get("estado").asText(),
                "escribir el diagnostico es lo que cierra la consulta");
    }

    @Test
    @DisplayName("el estado lo deriva el diagnostico, no lo declara el cliente")
    void elEstadoLoDerivaElDiagnostico() throws Exception {
        // Un cliente que se declara FINALIZADA sin diagnostico dejaria un
        // expediente que dice "atendido" sin una sola linea de lo que se
        // encontro.
        JsonNode sinDiagnostico = crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Control","estado":"FINALIZADA"}
                """.formatted(paciente));
        assertEquals("PENDIENTE", sinDiagnostico.get("estado").asText());

        JsonNode conDiagnostico = crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Control","diagnostico":"Sano","estado":"PENDIENTE"}
                """.formatted(paciente));
        assertEquals("FINALIZADA", conDiagnostico.get("estado").asText());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Actualizar: completar sin destruir
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("actualizar el motivo no borra el diagnostico ya escrito")
    void actualizarElMotivoNoBorraElDiagnostico() throws Exception {
        JsonNode creada = crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Dolor de garganta","diagnostico":"Faringitis aguda"}
                """.formatted(paciente));
        long consultaId = creada.get("consultaId").asLong();

        String cuerpo = mockMvc.perform(put("/consultas/{id}", consultaId)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Dolor de garganta y fiebre\",\"diagnostico\":\"\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode actualizada = json.readTree(cuerpo);
        assertEquals("Dolor de garganta y fiebre", actualizada.get("motivo").asText());
        assertEquals("Faringitis aguda", actualizada.get("diagnostico").asText(),
                "un diagnostico no se borra mandandolo vacio; para eso hara falta un acto explicito");
        assertEquals("FINALIZADA", actualizada.get("estado").asText());
    }

    @Test
    @DisplayName("actualizar no puede mover la consulta al expediente de otro paciente")
    void actualizarNoCambiaDePaciente() throws Exception {
        // Cambiar el paciente de una consulta no es editarla: es mover un acto
        // medico de un expediente a otro.
        long otroPaciente = crearPaciente(medico);
        long consultaId = crearConsultaSimple(medico, paciente).get("consultaId").asLong();

        String cuerpo = mockMvc.perform(put("/consultas/{id}", consultaId)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d,\"motivo\":\"Control\"}".formatted(otroPaciente)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(paciente, json.readTree(cuerpo).get("paciente").get("personaId").asLong());
    }

    // ══════════════════════════════════════════════════════════════════════
    // La clinica es opcional
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("una consulta puede registrarse sin clinica, y otra con ella")
    void laClinicaEsOpcional() throws Exception {
        // Un medico que todavia no registro ninguna sucursal debe poder
        // atender; obligarlo a elegir una lo forzaria a inventarsela.
        JsonNode sinClinica = crearConsultaSimple(medico, paciente);
        assertTrue(sinClinica.get("clinica").isNull(), "la consulta no debio inventarse una clinica");

        // Y cuando si hay sucursal, se guarda: sin esto, "clinica siempre null"
        // tambien pasaria la mitad anterior.
        String clinica = mockMvc.perform(post("/clinics")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sucursal Santa Ana\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int clinicaId = json.readTree(clinica).get("clinicaId").asInt();

        JsonNode conClinica = crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Control","clinicaId":%d}
                """.formatted(paciente, clinicaId));

        assertEquals(clinicaId, conClinica.get("clinica").get("clinicaId").asInt());
        assertEquals("Sucursal Santa Ana", conClinica.get("clinica").get("name").asText());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Historial
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("el historial de un paciente sale completo, ordenado y sin consultas ajenas")
    void elHistorialSaleOrdenadoYSoloDelPaciente() throws Exception {
        long otroPaciente = crearPaciente(medico);

        // Se crean desordenadas a proposito, con fechas explicitas: si el
        // servicio devolviera el orden de insercion, esta prueba lo delata.
        crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Segunda visita","fecha":"2026-03-10T09:00:00"}
                """.formatted(paciente));
        crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Primera visita","fecha":"2026-01-05T09:00:00"}
                """.formatted(paciente));
        crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Tercera visita","fecha":"2026-06-20T09:00:00"}
                """.formatted(paciente));
        crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Consulta de otro paciente","fecha":"2026-07-01T09:00:00"}
                """.formatted(otroPaciente));

        JsonNode historial = json.readTree(
                mockMvc.perform(get("/consultas").param("pacienteId", String.valueOf(paciente))
                                .header("Authorization", bearer(medico)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());

        assertEquals(3, historial.size(), "el historial debe traer solo las consultas de este paciente");
        assertEquals("Tercera visita", historial.get(0).get("motivo").asText(), "falta el orden por fecha descendente");
        assertEquals("Segunda visita", historial.get(1).get("motivo").asText());
        assertEquals("Primera visita", historial.get(2).get("motivo").asText());

        for (JsonNode consulta : historial) {
            assertEquals(paciente, consulta.get("paciente").get("personaId").asLong());
        }
    }

    @Test
    @DisplayName("el listado trae el expediente del paciente y la especialidad del medico")
    void elListadoTraeLosDatosQueElFrontendEspera() throws Exception {
        // El contrato con el frontend: sin estos campos la pantalla del
        // historial tendria que pedir cada paciente y cada medico por separado.
        JsonNode consulta = crearConsultaSimple(medico, paciente);

        assertTrue(consulta.get("paciente").get("expediente").asText().startsWith("EXP-"));
        assertEquals("Medicina General", consulta.get("medico").get("especialidad").asText());
        assertTrue(consulta.get("medico").get("apellidos").asText().length() > 0);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Lo que no existe
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("una consulta de un paciente que no existe responde 404, no 500")
    void unPacienteInexistenteResponde404() throws Exception {
        long inexistente = 9_999_999L;

        mockMvc.perform(post("/consultas")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d,\"motivo\":\"Control\"}".formatted(inexistente)))
                .andExpect(status().isNotFound());

        // Y su historial tampoco es una lista vacia: eso se leeria como "este
        // paciente no tiene consultas", que es falso sobre alguien que no
        // existe.
        mockMvc.perform(get("/consultas").param("pacienteId", String.valueOf(inexistente))
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("una clinica que no existe responde 404 y no deja la consulta a medias")
    void unaClinicaInexistenteResponde404() throws Exception {
        mockMvc.perform(post("/consultas")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d,\"clinicaId\":987654,\"motivo\":\"Control\"}"
                                .formatted(paciente)))
                .andExpect(status().isNotFound());

        JsonNode historial = json.readTree(
                mockMvc.perform(get("/consultas").param("pacienteId", String.valueOf(paciente))
                                .header("Authorization", bearer(medico)))
                        .andReturn().getResponse().getContentAsString());

        assertEquals(0, historial.size(), "la consulta rechazada no debio quedar guardada");
    }

    @Test
    @DisplayName("ver, actualizar o borrar una consulta inexistente responde 404")
    void unaConsultaInexistenteResponde404() throws Exception {
        long inexistente = 9_999_999L;

        mockMvc.perform(get("/consultas/{id}", inexistente).header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/consultas/{id}", inexistente)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Control\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/consultas/{id}", inexistente).header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("borrar una consulta la saca del historial")
    void borrarUnaConsultaLaSacaDelHistorial() throws Exception {
        long consultaId = crearConsultaSimple(medico, paciente).get("consultaId").asLong();

        mockMvc.perform(delete("/consultas/{id}", consultaId).header("Authorization", bearer(medico)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/consultas/{id}", consultaId).header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Quien puede leer
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("la enfermera si puede leer las consultas; sin token nadie entra")
    void laEnfermeraLeePeroNoEscribe() throws Exception {
        // Enfermeria prepara al paciente y aplica indicaciones: negarle la
        // lectura obligaria al medico a dictarle lo que ya esta escrito.
        String enfermera = tokenDeEnfermera();
        long consultaId = crearConsultaSimple(medico, paciente).get("consultaId").asLong();

        mockMvc.perform(get("/consultas/{id}", consultaId).header("Authorization", bearer(enfermera)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/consultas").param("pacienteId", String.valueOf(paciente))
                        .header("Authorization", bearer(enfermera)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/consultas/{id}", consultaId).header("Authorization", bearer(enfermera)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/consultas")).andExpect(status().isUnauthorized());
    }

    private JsonNode leerConsulta(String token, long consultaId) throws Exception {
        return json.readTree(mockMvc.perform(get("/consultas/{id}", consultaId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
