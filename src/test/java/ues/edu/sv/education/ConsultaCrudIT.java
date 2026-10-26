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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
                {"pacienteId":%d,"motivo":"Control","diagnostico":"Control sin hallazgos","medicoId":%d,
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
        long consultaId = crearConsultaPendiente(medico, paciente).get("consultaId").asLong();

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
        long consultaId = crearConsultaPendiente(medico, paciente).get("consultaId").asLong();

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
        // pasaria las dos anteriores. Se parte de una consulta PENDIENTE (las
        // anteriores a HU-19, que nacian sin diagnostico).
        long consultaId = crearConsultaPendiente(medico, paciente).get("consultaId").asLong();

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
        // encontro. Desde HU-19 ni siquiera hay alta sin diagnostico (400);
        // el estado declarado se ignora y lo manda el diagnostico.
        tomarSignosVitales(paciente);
        mockMvc.perform(post("/consultas")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d,\"motivo\":\"Control\",\"estado\":\"FINALIZADA\"}"
                                .formatted(paciente)))
                .andExpect(status().isBadRequest());

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
                        // Desde HU-26 una clinica exige su direccion completa; lo
                        // que esta prueba comprueba es otra cosa -- que la consulta
                        // guarde la sucursal -- asi que se la da y sigue a lo suyo.
                        .content("""
                                {"name":"Sucursal Santa Ana","departamento":"Santa Ana",
                                 "municipio":"Santa Ana","direccion":"Calle Principal",
                                 "telefono":"2440-0000","horario":"Lunes a viernes, 8:00 a 16:00",
                                 "latitud":13.9942,"longitud":-89.5597}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int clinicaId = json.readTree(clinica).get("clinicaId").asInt();

        JsonNode conClinica = crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Control","diagnostico":"Control sin hallazgos","clinicaId":%d}
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
                {"pacienteId":%d,"motivo":"Segunda visita","diagnostico":"Control sin hallazgos","fecha":"2026-03-10T09:00:00"}
                """.formatted(paciente));
        crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Primera visita","diagnostico":"Control sin hallazgos","fecha":"2026-01-05T09:00:00"}
                """.formatted(paciente));
        crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Tercera visita","diagnostico":"Control sin hallazgos","fecha":"2026-06-20T09:00:00"}
                """.formatted(paciente));
        crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Consulta de otro paciente","diagnostico":"Control sin hallazgos","fecha":"2026-07-01T09:00:00"}
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
                        .content("{\"pacienteId\":%d,\"motivo\":\"Control\",\"diagnostico\":\"Sano\"}".formatted(inexistente)))
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
                        .content("{\"pacienteId\":%d,\"clinicaId\":987654,\"motivo\":\"Control\",\"diagnostico\":\"Sano\"}"
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

        // Sin DELETE: la ruta ya no admite ese metodo, asi que un id inexistente
        // da 405 antes de que nadie mire si la consulta existe. Se comprueba en
        // unaConsultaNoSePuedeBorrar.
    }

    @Test
    @DisplayName("una consulta NO se puede borrar, y sigue en el historial")
    void unaConsultaNoSePuedeBorrar() throws Exception {
        // Esta prueba afirmaba lo contrario: que borrar sacaba la consulta del
        // historial, y pasaba. El endpoint existia y borraba de verdad,
        // llevandose las recetas por el ON DELETE CASCADE de V6.
        //
        // HU-21 (DRS-91) lo prohibe sin margen: "el sistema NUNCA ejecuta un
        // DELETE sobre una consulta. Anular es un cambio de estado, y esa
        // distincion es la que hace defendible el expediente ante una
        // auditoria". Una consulta equivocada OCURRIO -- puede haberse
        // entregado una receta a partir de ella -- y hacerla desaparecer no
        // corrige el error, lo esconde.
        //
        // Se responde 405 y no 404 porque la ruta si existe: lo que no existe
        // es esa operacion sobre ella.
        long consultaId = crearConsultaSimple(medico, paciente).get("consultaId").asLong();

        mockMvc.perform(delete("/consultas/{id}", consultaId).header("Authorization", bearer(medico)))
                .andExpect(status().isMethodNotAllowed());

        // Y lo que de verdad importa: sigue ahi.
        mockMvc.perform(get("/consultas/{id}", consultaId).header("Authorization", bearer(medico)))
                .andExpect(status().isOk());
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

        // Enfermeria tampoco borra, pero ya no por falta de permiso: nadie
        // borra. El 405 llega antes que cualquier comprobacion de rol.
        mockMvc.perform(delete("/consultas/{id}", consultaId).header("Authorization", bearer(enfermera)))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(get("/consultas")).andExpect(status().isUnauthorized());
    }

    // ══════════════════════════════════════════════════════════════════════
    // HU-20 criterio 4 · el diagnostico no sale hacia enfermeria
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("enfermeria ve la consulta pero NO el texto del diagnostico")
    void enfermeriaNoRecibeElDiagnostico() throws Exception {
        // El recorte va en el servidor y no en la pantalla: esconderlo en la
        // interfaz deja el dato viajando en la respuesta, donde se lee con las
        // herramientas del navegador o llamando al endpoint directamente.
        long consultaId = crearConsultaPendiente(medico, paciente).get("consultaId").asLong();

        // La consulta nace PENDIENTE (como las anteriores a HU-19) y se le
        // escribe el diagnostico: asi hay algo que ocultar.
        mockMvc.perform(put("/consultas/{id}", consultaId)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diagnostico\":\"Faringitis aguda probablemente viral\"}"))
                .andExpect(status().isOk());

        JsonNode paraElMedico = leerConsulta(medico, consultaId);
        assertTrue(paraElMedico.hasNonNull("diagnostico"),
                "el medico si tiene que ver el diagnostico que el mismo escribio");

        JsonNode paraEnfermeria = leerConsulta(tokenDeEnfermera(), consultaId);
        assertTrue(paraEnfermeria.get("diagnostico").isNull(),
                "el texto del diagnostico no debe salir hacia enfermeria");

        // Lo demas si le llega: el historial vive en el expediente y enfermeria
        // abre expedientes. Necesita saber que hubo consulta, cuando y con
        // quien; lo que no le toca es el juicio clinico.
        assertTrue(paraEnfermeria.hasNonNull("fecha"), "la fecha si");
        assertTrue(paraEnfermeria.hasNonNull("motivo"), "el motivo si");
        assertTrue(paraEnfermeria.get("medico").hasNonNull("nombres"), "el medico si");
    }

    // ══════════════════════════════════════════════════════════════════════
    // HU-10 criterio 3 · un paciente de baja no recibe consultas nuevas
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("un paciente dado de baja no admite consultas nuevas, pero si se lee")
    void unPacienteDeBajaNoAdmiteConsultasNuevas() throws Exception {
        // La baja logica sacaba al paciente de los listados y no cambiaba nada
        // de lo que el sistema acepta: seguia siendo atendible por id.
        JsonNode previa = crearConsultaSimple(medico, paciente);

        // La baja es del administrador desde que se resolvio la Tabla 5.
        mockMvc.perform(delete("/pacientes/{id}", paciente).header("Authorization", bearer(tokenDeAdministrador())))
                .andExpect(status().isNoContent());

        tomarSignosVitales(paciente);
        mockMvc.perform(post("/consultas")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d,\"motivo\":\"Intento sobre paciente de baja\",\"diagnostico\":\"Sano\"}"
                                .formatted(paciente)))
                .andExpect(status().isConflict());

        // Y lo que el criterio SI permite: el expediente se sigue leyendo entero.
        mockMvc.perform(get("/consultas").param("pacienteId", String.valueOf(paciente))
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk());
        assertEquals(previa.get("consultaId").asLong(),
                leerConsulta(medico, previa.get("consultaId").asLong()).get("consultaId").asLong(),
                "las consultas anteriores a la baja siguen accesibles");
    }

    @Test
    @DisplayName("reactivar al paciente vuelve a permitir consultas")
    void reactivarVuelveAPermitirConsultas() throws Exception {
        // La contraparte: una prueba que solo mira el 409 se sigue cumpliendo
        // si alguien bloquea las consultas para todo el mundo.
        // La baja es del administrador desde que se resolvio la Tabla 5.
        mockMvc.perform(delete("/pacientes/{id}", paciente).header("Authorization", bearer(tokenDeAdministrador())))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/pacientes/{id}/estado", paciente)
                        .header("Authorization", bearer(tokenDeAdministrador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ACTIVO\"}"))
                .andExpect(status().isOk());

        assertTrue(crearConsultaSimple(medico, paciente).hasNonNull("consultaId"));
    }

    // ══════════════════════════════════════════════════════════════════════
    // HU-19 criterio 1 · motivo y diagnostico obligatorios; exploracion e
    // indicaciones opcionales
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("HU-19 c1: sin motivo o sin diagnostico la consulta se rechaza y no se guarda")
    void sinMotivoOSinDiagnosticoSeRechaza() throws Exception {
        tomarSignosVitales(paciente);

        for (String cuerpo : new String[]{
                "{\"pacienteId\":%d,\"diagnostico\":\"Faringitis\"}",
                "{\"pacienteId\":%d,\"motivo\":\"   \",\"diagnostico\":\"Faringitis\"}",
                "{\"pacienteId\":%d,\"motivo\":\"Dolor de garganta\"}",
                "{\"pacienteId\":%d,\"motivo\":\"Dolor de garganta\",\"diagnostico\":\"\"}",
                "{\"pacienteId\":%d,\"exploracionFisica\":\"Normal\",\"indicaciones\":\"Reposo\"}"}) {
            mockMvc.perform(post("/consultas")
                            .header("Authorization", bearer(medico))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo.formatted(paciente)))
                    .andExpect(status().isBadRequest());
        }

        JsonNode historial = json.readTree(
                mockMvc.perform(get("/consultas").param("pacienteId", String.valueOf(paciente))
                                .header("Authorization", bearer(medico)))
                        .andReturn().getResponse().getContentAsString());
        assertEquals(0, historial.size(), "una consulta rechazada quedo guardada");
    }

    @Test
    @DisplayName("HU-19 c1: la exploracion fisica y las indicaciones son opcionales, y se guardan cuando vienen")
    void exploracionEIndicacionesSonOpcionales() throws Exception {
        JsonNode sin = crearConsultaSimple(medico, paciente);
        assertTrue(sin.get("exploracionFisica").isNull());
        assertTrue(sin.get("indicaciones").isNull());

        JsonNode con = crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Dolor de garganta","diagnostico":"Faringitis aguda",
                 "exploracionFisica":"Faringe eritematosa","indicaciones":"Reposo e hidratacion"}
                """.formatted(paciente));
        assertEquals("Faringe eritematosa", con.get("exploracionFisica").asText());
        assertEquals("Reposo e hidratacion", con.get("indicaciones").asText());
        assertEquals("FINALIZADA", con.get("estado").asText());

        // Y se pueden completar despues.
        JsonNode editada = json.readTree(mockMvc.perform(put("/consultas/{id}", sin.get("consultaId").asLong())
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exploracionFisica\":\"Sin hallazgos\",\"indicaciones\":\"Control en un mes\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals("Sin hallazgos", editada.get("exploracionFisica").asText());
        assertEquals("Control en un mes", editada.get("indicaciones").asText());
    }

    @Test
    @DisplayName("HU-19 c2: una enfermera con un formulario incompleto recibe 403, no 400")
    void laEnfermeraRecibe403AunqueElFormularioEsteIncompleto() throws Exception {
        mockMvc.perform(post("/consultas")
                        .header("Authorization", bearer(tokenDeEnfermera()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d}".formatted(paciente)))
                .andExpect(status().isForbidden());
    }

    // ══════════════════════════════════════════════════════════════════════
    // HU-19 criterio 3 · medico, clinica y fecha no son editables
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("HU-19 c3: PUT no cambia el medico, la clinica ni la fecha de la consulta")
    void medicoClinicaYFechaSonInmutables() throws Exception {
        int clinicaOriginal = crearSede(medico);
        int otraClinica = crearSede(medico);
        JsonNode creada = crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Control","diagnostico":"Sano","clinicaId":%d,
                 "fecha":"2026-03-10T09:00:00"}
                """.formatted(paciente, clinicaOriginal));
        long consultaId = creada.get("consultaId").asLong();
        long medicoOriginal = creada.get("medico").get("personaId").asLong();

        String otroMedico = tokenDeMedico();
        long idOtroMedico = crearConsultaSimple(otroMedico, crearPaciente(otroMedico))
                .get("medico").get("personaId").asLong();

        // El motivo si se corrige, y aun asi
        // estos tres campos no se mueven aunque los mande.
        JsonNode editada = json.readTree(mockMvc.perform(put("/consultas/{id}", consultaId)
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"motivo":"Motivo corregido","clinicaId":%d,"fecha":"2030-01-01T00:00:00",
                                 "medicoId":%d,"medico":{"personaId":%d}}
                                """.formatted(otraClinica, idOtroMedico, idOtroMedico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertEquals("Motivo corregido", editada.get("motivo").asText(), "lo editable si debe cambiar");
        assertEquals(clinicaOriginal, editada.get("clinica").get("clinicaId").asInt(), "la clinica cambio");
        assertEquals("2026-03-10T09:00:00", editada.get("fecha").asText(), "la fecha cambio");
        assertEquals(medicoOriginal, editada.get("medico").get("personaId").asLong(), "el medico cambio");

        // Otro medico tampoco se la queda: sigue firmada por quien la registro.
        JsonNode porOtro = json.readTree(mockMvc.perform(put("/consultas/{id}", consultaId)
                        .header("Authorization", bearer(otroMedico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"indicaciones\":\"Agregado por otro\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(medicoOriginal, porOtro.get("medico").get("personaId").asLong());
    }

    // ══════════════════════════════════════════════════════════════════════
    // HU-20 · historial con medico y clinica; filtros por fechas y medico
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("HU-20 c1: cada consulta del historial trae fecha, medico, clinica y motivo")
    void elHistorialTraeMedicoYClinica() throws Exception {
        int clinicaId = crearSede(medico);
        crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Control","diagnostico":"Sano","clinicaId":%d}
                """.formatted(paciente, clinicaId));

        JsonNode fila = listar(medico, "pacienteId", String.valueOf(paciente)).get(0);

        assertTrue(fila.hasNonNull("fecha"));
        assertEquals("Control", fila.get("motivo").asText());
        assertTrue(fila.get("medico").hasNonNull("nombres"));
        assertTrue(fila.get("medico").hasNonNull("apellidos"));
        assertEquals(clinicaId, fila.get("clinica").get("clinicaId").asInt());
        assertTrue(fila.get("clinica").hasNonNull("name"));
    }

    @Test
    @DisplayName("HU-20 c2: filtrar por rango de fechas incluye el dia completo de 'hasta'")
    void filtrarPorRangoDeFechas() throws Exception {
        crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Enero","diagnostico":"Sano","fecha":"2026-01-05T09:00:00"}
                """.formatted(paciente));
        crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Marzo temprano","diagnostico":"Sano","fecha":"2026-03-10T00:00:00"}
                """.formatted(paciente));
        crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Marzo tarde","diagnostico":"Sano","fecha":"2026-03-20T23:59:30"}
                """.formatted(paciente));
        crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Junio","diagnostico":"Sano","fecha":"2026-06-20T09:00:00"}
                """.formatted(paciente));

        // Los dos extremos del rango caen en el borde del dia y deben entrar.
        JsonNode marzo = listar(medico, "pacienteId", String.valueOf(paciente),
                "desde", "2026-03-10", "hasta", "2026-03-20");
        assertEquals(2, marzo.size());
        assertEquals("Marzo tarde", marzo.get(0).get("motivo").asText());
        assertEquals("Marzo temprano", marzo.get(1).get("motivo").asText());

        // Solo desde / solo hasta.
        assertEquals(3, listar(medico, "pacienteId", String.valueOf(paciente), "desde", "2026-03-10").size());
        assertEquals(1, listar(medico, "pacienteId", String.valueOf(paciente), "hasta", "2026-01-05").size());

        // Un rango sin consultas es una lista vacia, no un error.
        assertEquals(0, listar(medico, "pacienteId", String.valueOf(paciente),
                "desde", "2027-01-01", "hasta", "2027-12-31").size());

        // Rango al reves: 400.
        mockMvc.perform(get("/consultas").param("desde", "2026-06-01").param("hasta", "2026-01-01")
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("HU-20 c2: filtrar por medico trae solo sus consultas, y se combina con paciente y fechas")
    void filtrarPorMedico() throws Exception {
        String otroMedico = tokenDeMedico();
        long idMio = crearConsultaSimple(medico, paciente).get("medico").get("personaId").asLong();
        crearConsultaSimple(medico, paciente);
        JsonNode deOtro = crearConsultaSimple(otroMedico, paciente);
        long idOtro = deOtro.get("medico").get("personaId").asLong();
        assertNotEquals(idMio, idOtro);

        JsonNode mias = listar(medico, "pacienteId", String.valueOf(paciente), "medicoId", String.valueOf(idMio));
        assertEquals(2, mias.size());
        mias.forEach(c -> assertEquals(idMio, c.get("medico").get("personaId").asLong()));

        JsonNode delOtro = listar(medico, "pacienteId", String.valueOf(paciente), "medicoId", String.valueOf(idOtro));
        assertEquals(1, delOtro.size());
        assertEquals(deOtro.get("consultaId").asLong(), delOtro.get(0).get("consultaId").asLong());

        // Sin filtro de medico, las tres.
        assertEquals(3, listar(medico, "pacienteId", String.valueOf(paciente)).size());

        // Combinado con un rango que no incluye hoy: ninguna.
        assertEquals(0, listar(medico, "pacienteId", String.valueOf(paciente),
                "medicoId", String.valueOf(idMio), "hasta", "2020-01-01").size());

        // Un medico que no existe es un filtro que nadie cumple.
        assertEquals(0, listar(medico, "pacienteId", String.valueOf(paciente), "medicoId", "9999999").size());
    }

    @Test
    @DisplayName("HU-20 c3: la consulta se abre con todos sus datos")
    void laConsultaSeAbreConTodosSusDatos() throws Exception {
        JsonNode creada = crearConsulta(medico, """
                {"pacienteId":%d,"motivo":"Tos","diagnostico":"Bronquitis","exploracionFisica":"Sibilancias",
                 "indicaciones":"Inhalador"}
                """.formatted(paciente));

        JsonNode abierta = leerConsulta(medico, creada.get("consultaId").asLong());
        assertEquals("Tos", abierta.get("motivo").asText());
        assertEquals("Bronquitis", abierta.get("diagnostico").asText());
        assertEquals("Sibilancias", abierta.get("exploracionFisica").asText());
        assertEquals("Inhalador", abierta.get("indicaciones").asText());
        assertEquals(paciente, abierta.get("paciente").get("personaId").asLong());
    }

    @Test
    @DisplayName("HU-20 c4: el listado filtrado tampoco entrega el diagnostico a enfermeria")
    void elListadoFiltradoNoEntregaElDiagnosticoAEnfermeria() throws Exception {
        crearConsultaSimple(medico, paciente);

        JsonNode fila = listar(tokenDeEnfermera(), "pacienteId", String.valueOf(paciente),
                "desde", "2020-01-01").get(0);

        assertTrue(fila.get("diagnostico").isNull());
        assertTrue(fila.hasNonNull("motivo"));
    }

    private JsonNode listar(String token, String... parametros) throws Exception {
        var peticion = get("/consultas").header("Authorization", bearer(token));
        for (int i = 0; i < parametros.length; i += 2) {
            peticion.param(parametros[i], parametros[i + 1]);
        }
        return json.readTree(mockMvc.perform(peticion)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode leerConsulta(String token, long consultaId) throws Exception {
        return json.readTree(mockMvc.perform(get("/consultas/{id}", consultaId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
