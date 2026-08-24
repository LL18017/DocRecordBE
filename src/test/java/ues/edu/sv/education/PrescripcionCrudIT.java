package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prescripciones (epica E7) a traves de la API real.
 *
 * Las reglas que se prueban son las que hacen de una receta un documento y no
 * una nota: que la firme quien la emite, que no exista vacia, y que no
 * sobreviva sin la consulta que la origino.
 */
class PrescripcionCrudIT extends PruebaClinica {

    @Autowired private JdbcTemplate jdbc;

    private String medico;
    private long paciente;
    private long consultaId;

    @BeforeEach
    void prepararUnaConsulta() throws Exception {
        medico = tokenDeMedico();
        paciente = crearPaciente(medico);
        consultaId = crearConsultaSimple(medico, paciente).get("consultaId").asLong();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Una receta sin medicamentos no es una receta
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("una receta con la lista de medicamentos vacia se rechaza y no se guarda")
    void unaRecetaVaciaSeRechaza() throws Exception {
        mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultaId\":%d,\"medicamentos\":[]}".formatted(consultaId)))
                .andExpect(status().isBadRequest());

        // Sin la lista siquiera: un cliente que se olvida del campo tampoco
        // puede emitir un papel firmado en blanco.
        mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultaId\":%d}".formatted(consultaId)))
                .andExpect(status().isBadRequest());

        // Un renglon sin nombre de medicamento no llena la receta: no indica
        // nada, solo ocupa lugar.
        mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consultaId":%d,"medicamentos":[{"dosis":"1 tableta"}]}
                                """.formatted(consultaId)))
                .andExpect(status().isBadRequest());

        assertEquals(0, recetasDeLaConsulta().size(),
                "ninguna de las peticiones rechazadas debio dejar una receta");
    }

    // ══════════════════════════════════════════════════════════════════════
    // La firma
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("la receta la firma el medico autenticado, no el que venga en el cuerpo")
    void laFirmaEsLaDelMedicoAutenticado() throws Exception {
        // La firma es lo que hace de la receta un documento con responsable.
        // Aceptarla del cuerpo permitiria recetar en nombre de otro medico.
        String otroMedico = tokenDeMedico();
        long idDelOtroMedico = crearConsultaSimple(otroMedico, paciente)
                .get("medico").get("personaId").asLong();

        JsonNode receta = emitir(medico, """
                {"consultaId":%d,"medicoId":%d,
                 "medicamentos":[{"medicamento":"Amoxicilina 500 mg","dosis":"1 tableta",
                                  "frecuencia":"Cada 8 horas","duracion":"7 dias"}]}
                """.formatted(consultaId, idDelOtroMedico));

        assertNotEquals(idDelOtroMedico, receta.get("medico").get("personaId").asLong(),
                "la receta quedo firmada por el medico que venia en el cuerpo");
    }

    @Test
    @DisplayName("la receta que firma un medico de guardia queda a su nombre, no al del que atendio")
    void laFirmaPuedeSerDeUnMedicoDistintoAlQueAtendio() throws Exception {
        // Es la razon de que prescripciones.medico_id exista aunque la consulta
        // ya tenga medico: en una clinica con turnos, quien receta no siempre
        // es quien abrio la consulta, y la firma debe decir la verdad.
        String medicoDeGuardia = tokenDeMedico();

        JsonNode receta = emitir(medicoDeGuardia, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Ibuprofeno 400 mg"}]}
                """.formatted(consultaId));

        long medicoQueAtendio = json.readTree(
                mockMvc.perform(get("/consultas/{id}", consultaId).header("Authorization", bearer(medico)))
                        .andReturn().getResponse().getContentAsString())
                .get("medico").get("personaId").asLong();

        assertNotEquals(medicoQueAtendio, receta.get("medico").get("personaId").asLong(),
                "la firma se dedujo de la consulta en vez de guardarse");
    }

    // ══════════════════════════════════════════════════════════════════════
    // Contenido
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("la receta guarda cada medicamento con su dosis, frecuencia y duracion")
    void laRecetaGuardaCadaRenglon() throws Exception {
        JsonNode receta = emitir(medico, """
                {"consultaId":%d,"medicamentos":[
                   {"medicamento":"Amoxicilina 500 mg","dosis":"1 tableta","frecuencia":"Cada 8 horas","duracion":"7 dias"},
                   {"medicamento":"Suspender el antibiotico anterior"}
                ]}
                """.formatted(consultaId));

        JsonNode renglones = receta.get("medicamentos");
        assertEquals(2, renglones.size());

        assertNotNull(renglones.get(0).get("id"));
        assertEquals("Amoxicilina 500 mg", renglones.get(0).get("medicamento").asText());
        assertEquals("Cada 8 horas", renglones.get(0).get("frecuencia").asText());
        assertEquals("7 dias", renglones.get(0).get("duracion").asText());

        // Una indicacion sin dosis es legitima y debe poder guardarse.
        assertEquals("Suspender el antibiotico anterior", renglones.get(1).get("medicamento").asText());
        assertTrue(renglones.get(1).get("dosis").isNull());

        assertEquals(consultaId, receta.get("consultaId").asLong());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Borrar la consulta se lleva sus recetas
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("borrar una consulta arrastra sus recetas y los medicamentos de estas")
    void borrarLaConsultaArrastraSusRecetas() throws Exception {
        long primera = emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Amoxicilina 500 mg"},
                                                 {"medicamento":"Paracetamol 500 mg"}]}
                """.formatted(consultaId)).get("prescripcionId").asLong();

        long segunda = emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Loratadina 10 mg"}]}
                """.formatted(consultaId)).get("prescripcionId").asLong();

        assertEquals(3, medicamentosGuardados(primera, segunda), "no se guardaron los renglones");

        mockMvc.perform(delete("/consultas/{id}", consultaId).header("Authorization", bearer(medico)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/prescripciones/{id}", primera).header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/prescripciones/{id}", segunda).header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());

        // Los renglones tambien: si solo cayeran las recetas, la tabla de
        // medicamentos quedaria con filas apuntando a recetas que ya no
        // existen. Se comprueba contra la base porque esa cascada la impone
        // PostgreSQL (V6), no el codigo Java.
        assertEquals(0, medicamentosGuardados(primera, segunda),
                "quedaron medicamentos huerfanos de una receta borrada");
    }

    // ══════════════════════════════════════════════════════════════════════
    // Listados
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("las recetas de un paciente salen todas, ordenadas y sin las de otro paciente")
    void listarPorPacienteDevuelveTodasSusRecetas() throws Exception {
        long otraConsulta = crearConsultaSimple(medico, paciente).get("consultaId").asLong();
        long otroPaciente = crearPaciente(medico);
        long consultaAjena = crearConsultaSimple(medico, otroPaciente).get("consultaId").asLong();

        long primera = emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Amoxicilina 500 mg"}]}
                """.formatted(consultaId)).get("prescripcionId").asLong();
        long segunda = emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Loratadina 10 mg"}]}
                """.formatted(otraConsulta)).get("prescripcionId").asLong();
        emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Receta de otro paciente"}]}
                """.formatted(consultaAjena));

        JsonNode delPaciente = contenidoDe(mockMvc.perform(get("/prescripciones")
                        .param("pacienteId", String.valueOf(paciente))
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertEquals(2, delPaciente.size(), "deben salir las recetas de TODAS sus consultas, y solo las suyas");
        assertEquals(segunda, delPaciente.get(0).get("prescripcionId").asLong(),
                "la mas reciente va primero");
        assertEquals(primera, delPaciente.get(1).get("prescripcionId").asLong());

        // Por consulta, solo la de esa consulta.
        JsonNode deLaConsulta = recetasDeLaConsulta();
        assertEquals(1, deLaConsulta.size());
        assertEquals(primera, deLaConsulta.get(0).get("prescripcionId").asLong());
    }

    @Test
    @DisplayName("filtrar solo por medico devuelve unicamente las recetas que el firmo")
    void listarPorMedicoDevuelveSoloLasQueElFirmo() throws Exception {
        // Medico nuevo, exclusivo de esta prueba: al ser un id que ninguna
        // otra prueba de la suite pudo haber usado, el conteo exacto es
        // valido aunque la base de pruebas acumule datos de otros casos en la
        // misma corrida (PruebaDeIntegracion recrea la base una vez por
        // corrida completa, no por prueba individual).
        String otroMedico = tokenDeMedico();
        JsonNode consultaDeOtroMedico = crearConsultaSimple(otroMedico, paciente);
        long medicoId = consultaDeOtroMedico.get("medico").get("personaId").asLong();
        long suConsulta = consultaDeOtroMedico.get("consultaId").asLong();

        long recetaDelOtroMedico = emitir(otroMedico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Metformina 850 mg"}]}
                """.formatted(suConsulta)).get("prescripcionId").asLong();

        // El medico del setUp receta al MISMO paciente, para probar que el
        // filtro es por medico y no se cuela por compartir paciente.
        emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Amoxicilina 500 mg"}]}
                """.formatted(consultaId));

        JsonNode delMedico = contenidoDe(mockMvc.perform(get("/prescripciones")
                        .param("medicoId", String.valueOf(medicoId))
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertEquals(1, delMedico.size(), "solo debe salir la receta firmada por ese medico");
        assertEquals(recetaDelOtroMedico, delMedico.get(0).get("prescripcionId").asLong());
    }

    @Test
    @DisplayName("pacienteId y medicoId combinados filtran por los dos a la vez -- antes esto daba 400")
    void listarCombinandoPacienteYMedicoFiltraPorAmbos() throws Exception {
        String medicoB = tokenDeMedico();
        JsonNode consultaDeB = crearConsultaSimple(medicoB, paciente);
        long medicoBId = consultaDeB.get("medico").get("personaId").asLong();

        long recetaDeA = emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Amoxicilina 500 mg"}]}
                """.formatted(consultaId)).get("prescripcionId").asLong();
        long recetaDeB = emitir(medicoB, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Losartan 50 mg"}]}
                """.formatted(consultaDeB.get("consultaId").asLong())).get("prescripcionId").asLong();

        JsonNode combinado = contenidoDe(mockMvc.perform(get("/prescripciones")
                        .param("pacienteId", String.valueOf(paciente))
                        .param("medicoId", String.valueOf(medicoBId))
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertEquals(1, combinado.size(),
                "con los dos filtros a la vez solo debe salir la receta que cumple AMBOS");
        assertEquals(recetaDeB, combinado.get(0).get("prescripcionId").asLong());
        assertNotEquals(recetaDeA, combinado.get(0).get("prescripcionId").asLong());
    }

    @Test
    @DisplayName("desde/hasta filtran por fecha e incluyen el DIA COMPLETO de hasta, hasta el ultimo instante")
    void desdeHastaIncluyenElDiaCompletoDeHasta() throws Exception {
        LocalDate dia = LocalDate.of(2024, 3, 10);

        long enElLimiteDeHasta = emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Justo en el limite de hasta"}]}
                """.formatted(consultaId)).get("prescripcionId").asLong();
        // El caso critico: 23:59:59.999 del MISMO dia que "hasta". Un filtro
        // ingenuo (fecha <= hasta interpretado como las 00:00:00 de hasta) se
        // comeria esta receta, aunque "hasta ese dia" claramente deberia
        // incluirla.
        fijarFecha(enElLimiteDeHasta, dia.atTime(23, 59, 59, 999_000_000));

        long unDiaAntesDeDesde = emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Un dia antes del rango"}]}
                """.formatted(consultaId)).get("prescripcionId").asLong();
        fijarFecha(unDiaAntesDeDesde, dia.minusDays(1).atTime(23, 59, 59));

        long justoDespuesDeHasta = emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Un instante despues del rango"}]}
                """.formatted(consultaId)).get("prescripcionId").asLong();
        fijarFecha(justoDespuesDeHasta, dia.plusDays(1).atStartOfDay().plusNanos(1000));

        JsonNode delRango = contenidoDe(mockMvc.perform(get("/prescripciones")
                        .param("pacienteId", String.valueOf(paciente))
                        .param("desde", dia.toString())
                        .param("hasta", dia.toString())
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertEquals(1, delRango.size(),
                "solo la receta del limite de hasta (23:59:59.999 de ese dia) debe caer en el rango");
        assertEquals(enElLimiteDeHasta, delRango.get(0).get("prescripcionId").asLong());
    }

    @Test
    @DisplayName("el orden es de la receta mas reciente a la mas antigua, no el orden de insercion")
    void elOrdenEsDescendentePorFechaNoPorInsercion() throws Exception {
        long antigua = emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"La mas antigua"}]}
                """.formatted(consultaId)).get("prescripcionId").asLong();
        long reciente = emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"La mas reciente"}]}
                """.formatted(consultaId)).get("prescripcionId").asLong();
        long intermedia = emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"La intermedia"}]}
                """.formatted(consultaId)).get("prescripcionId").asLong();

        // Se insertaron en el orden antigua -> reciente -> intermedia: ni el
        // orden de insercion ni el de prescripcionId coinciden con el orden
        // cronologico que se les asigna aqui. Si la respuesta reflejara el
        // orden natural de la tabla (o el de los ids) en vez de ORDER BY
        // fecha, esta prueba lo detecta.
        LocalDateTime base = LocalDateTime.of(2024, 5, 1, 8, 0, 0);
        fijarFecha(antigua, base);
        fijarFecha(reciente, base.plusDays(2));
        fijarFecha(intermedia, base.plusDays(1));

        JsonNode contenido = contenidoDe(mockMvc.perform(get("/prescripciones")
                        .param("pacienteId", String.valueOf(paciente))
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertEquals(3, contenido.size());
        assertEquals(reciente, contenido.get(0).get("prescripcionId").asLong(), "la mas reciente primero");
        assertEquals(intermedia, contenido.get(1).get("prescripcionId").asLong(), "la intermedia en medio");
        assertEquals(antigua, contenido.get(2).get("prescripcionId").asLong(), "la mas antigua al final");
    }

    @Test
    @DisplayName("el campo paciente del listado viene relleno y coincide con el paciente real de la receta")
    void elCampoPacienteVieneRellenoYCorrecto() throws Exception {
        JsonNode datosPaciente = crearPacienteConDatos(medico);
        long personaIdPaciente = datosPaciente.get("personaId").asLong();
        String expediente = datosPaciente.get("expediente").asText();
        String nombres = datosPaciente.get("persona").get("nombres").asText();
        String apellidos = datosPaciente.get("persona").get("apellidos").asText();

        long suConsulta = crearConsultaSimple(medico, personaIdPaciente).get("consultaId").asLong();
        emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Amoxicilina 500 mg"}]}
                """.formatted(suConsulta));

        JsonNode contenido = contenidoDe(mockMvc.perform(get("/prescripciones")
                        .param("pacienteId", String.valueOf(personaIdPaciente))
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertEquals(1, contenido.size());
        JsonNode pacienteEnLaReceta = contenido.get(0).get("paciente");
        assertNotNull(pacienteEnLaReceta, "el DTO de la receta debe traer el paciente");
        assertEquals(personaIdPaciente, pacienteEnLaReceta.get("personaId").asLong());
        assertEquals(expediente, pacienteEnLaReceta.get("expediente").asText());
        assertEquals(nombres, pacienteEnLaReceta.get("nombres").asText());
        assertEquals(apellidos, pacienteEnLaReceta.get("apellidos").asText());
    }

    @Test
    @DisplayName("sin ningun filtro se devuelve el historico completo, paginado (ya no 400)")
    void listarSinFiltroDevuelveHistoricoCompletoPaginado() throws Exception {
        // Datos de un paciente y un medico DISTINTOS a los del setUp, para
        // probar que sin filtro el historico cruza pacientes y medicos.
        String otroMedico = tokenDeMedico();
        long otroPaciente = crearPaciente(otroMedico);
        long otraConsulta = crearConsultaSimple(otroMedico, otroPaciente).get("consultaId").asLong();

        long recetaDeA = emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Amoxicilina 500 mg"}]}
                """.formatted(consultaId)).get("prescripcionId").asLong();
        long recetaDeB = emitir(otroMedico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Losartan 50 mg"}]}
                """.formatted(otraConsulta)).get("prescripcionId").asLong();

        // Pagina grande a proposito: no se puede exigir un totalElementos
        // exacto porque la base de pruebas se recrea una vez por CORRIDA
        // completa (no por prueba), asi que otras pruebas de la suite dejan
        // sus propias recetas ahi. Lo que si se puede exigir es que las DOS
        // que se acaban de crear, de pacientes y medicos distintos, aparezcan
        // ambas. 100 (TAMANO_PAGINA_MAXIMO en PrescripcionService) ya es una
        // pagina grande y alcanza para eso sin violar el tope: pedir 500
        // fallaria con 400, y ese tope es deliberado (ver el comentario de
        // TAMANO_PAGINA_MAXIMO en el servicio), no algo que este test deba
        // esquivar subiendo el limite.
        JsonNode respuesta = json.readTree(mockMvc.perform(get("/prescripciones")
                        .param("tamano", "100")
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertTrue(respuesta.has("contenido"));
        assertTrue(respuesta.has("totalElementos"));
        assertTrue(respuesta.has("totalPaginas"));
        assertTrue(respuesta.has("paginaActual"));

        List<Long> ids = new ArrayList<>();
        respuesta.get("contenido").forEach(nodo -> ids.add(nodo.get("prescripcionId").asLong()));

        assertTrue(ids.contains(recetaDeA),
                "el historico sin filtro debe incluir recetas de cualquier paciente");
        assertTrue(ids.contains(recetaDeB),
                "... y de cualquier medico, no solo el que hace la peticion");
        assertTrue(respuesta.get("totalElementos").asLong() >= 2);
    }

    @Test
    @DisplayName("la paginacion siempre informa el total: el cliente nunca tiene que adivinar si le falta algo")
    void laPaginacionInformaElTotalAunqueLaPaginaVengaIncompleta() throws Exception {
        // Tres recetas de un paciente FRESCO (exclusivo de esta prueba): el
        // total esperado es exacto y conocido, sin depender de lo que dejaron
        // otras pruebas de la suite.
        long consulta1 = crearConsultaSimple(medico, paciente).get("consultaId").asLong();
        long consulta2 = crearConsultaSimple(medico, paciente).get("consultaId").asLong();
        emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Uno"}]}
                """.formatted(consultaId));
        emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Dos"}]}
                """.formatted(consulta1));
        emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Tres"}]}
                """.formatted(consulta2));

        JsonNode primeraPagina = json.readTree(mockMvc.perform(get("/prescripciones")
                        .param("pacienteId", String.valueOf(paciente))
                        .param("tamano", "2")
                        .param("pagina", "0")
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertEquals(2, primeraPagina.get("contenido").size(), "la pagina trae solo 2, como se pidio");
        assertEquals(3, primeraPagina.get("totalElementos").asLong(),
                "pero el total dice 3: el cliente sabe que le falta una pagina, no adivina");
        assertEquals(2, primeraPagina.get("totalPaginas").asInt());
        assertEquals(0, primeraPagina.get("paginaActual").asInt());
    }

    @Test
    @DisplayName("pacienteId/medicoId/consultaId que no existen no dan 404: dan una pagina vacia")
    void filtrosConIdInexistenteDevuelvenPaginaVacia() throws Exception {
        // Decision explicita (ver el javadoc de PrescripcionService.listar):
        // a diferencia de ConsultaService (un pacienteId inexistente da 404
        // porque ahi ES la pregunta completa), aqui los filtros son varios,
        // opcionales y combinables, y la pregunta es "que cumple estas
        // condiciones" -- un id que no existe es, para ese proposito, una
        // condicion que nadie cumple, como un rango de fechas sin resultados.
        JsonNode porPacienteInexistente = json.readTree(mockMvc.perform(get("/prescripciones")
                        .param("pacienteId", "9999999")
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(0, porPacienteInexistente.get("contenido").size());
        assertEquals(0, porPacienteInexistente.get("totalElementos").asLong());

        JsonNode porMedicoInexistente = json.readTree(mockMvc.perform(get("/prescripciones")
                        .param("medicoId", "9999999")
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(0, porMedicoInexistente.get("contenido").size());

        JsonNode porConsultaInexistente = json.readTree(mockMvc.perform(get("/prescripciones")
                        .param("consultaId", "9999999")
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(0, porConsultaInexistente.get("contenido").size());

        // GET /prescripciones/{id} sigue siendo 404 para un id inexistente:
        // ahi si se pide UN recurso especifico por su identificador (como
        // /pacientes/{id}), no se filtra una coleccion.
        mockMvc.perform(get("/prescripciones/{id}", 9999999)
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("una receta sobre una consulta que no existe responde 404, no 500")
    void unaConsultaInexistenteResponde404() throws Exception {
        mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(medico))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consultaId":9999999,"medicamentos":[{"medicamento":"Amoxicilina 500 mg"}]}
                                """))
                .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Quien puede que
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("la enfermera lee las recetas pero no las emite")
    void laEnfermeraLeePeroNoReceta() throws Exception {
        // Enfermeria es quien administra lo recetado: una receta que no puede
        // leer no sirve de nada. Firmarla es otra cosa.
        String enfermera = tokenDeEnfermera();

        emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Amoxicilina 500 mg"}]}
                """.formatted(consultaId));

        mockMvc.perform(get("/prescripciones").param("consultaId", String.valueOf(consultaId))
                        .header("Authorization", bearer(enfermera)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(enfermera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consultaId":%d,"medicamentos":[{"medicamento":"Amoxicilina 500 mg"}]}
                                """.formatted(consultaId)))
                .andExpect(status().isForbidden());

        assertEquals(1, recetasDeLaConsulta().size(), "la enfermera emitio una receta");
    }

    @Test
    @DisplayName("un administrador no puede recetar: no existe recetar en nombre de otro")
    void unAdministradorNoPuedeRecetar() throws Exception {
        String admin = tokenDeAdminQueNoEjerce();

        mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"consultaId":%d,"medicamentos":[{"medicamento":"Amoxicilina 500 mg"}]}
                                """.formatted(consultaId)))
                .andExpect(status().isForbidden());

        assertEquals(0, recetasDeLaConsulta().size());
    }

    @Test
    @DisplayName("anular una receta la borra con todos sus medicamentos")
    void anularUnaRecetaLaBorraEntera() throws Exception {
        long recetaId = emitir(medico, """
                {"consultaId":%d,"medicamentos":[{"medicamento":"Amoxicilina 500 mg"},
                                                 {"medicamento":"Paracetamol 500 mg"}]}
                """.formatted(consultaId)).get("prescripcionId").asLong();

        mockMvc.perform(delete("/prescripciones/{id}", recetaId).header("Authorization", bearer(medico)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/prescripciones/{id}", recetaId).header("Authorization", bearer(medico)))
                .andExpect(status().isNotFound());

        // Media receta -- unos medicamentos si y otros no -- seria un documento
        // distinto del que firmo el medico.
        assertEquals(0, medicamentosGuardados(recetaId, recetaId));

        // La consulta sobrevive: la cascada va en un solo sentido.
        mockMvc.perform(get("/consultas/{id}", consultaId).header("Authorization", bearer(medico)))
                .andExpect(status().isOk());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Apoyo
    // ══════════════════════════════════════════════════════════════════════

    private JsonNode emitir(String token, String cuerpoJson) throws Exception {
        return json.readTree(mockMvc.perform(post("/prescripciones")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode recetasDeLaConsulta() throws Exception {
        return contenidoDe(mockMvc.perform(get("/prescripciones")
                        .param("consultaId", String.valueOf(consultaId))
                        .header("Authorization", bearer(medico)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** El arreglo "contenido" de una respuesta paginada de /prescripciones. */
    private JsonNode contenidoDe(String cuerpoJson) throws Exception {
        return json.readTree(cuerpoJson).get("contenido");
    }

    /**
     * Dispone la fecha de una receta ya emitida a un valor exacto, para las
     * pruebas de rango de fechas y de orden.
     *
     * Necesario porque PrescripcionRequestDto no acepta fecha (la pone el
     * servidor con LocalDateTime.now() al emitir, ver PrescripcionService.crear):
     * la unica forma de controlar el instante exacto de una receta para
     * probar un limite de rango es corrigiendolo despues, directo en la base.
     */
    private void fijarFecha(long prescripcionId, LocalDateTime fecha) {
        jdbc.update("UPDATE prescripciones SET fecha = ? WHERE prescripcion_id = ?", fecha, prescripcionId);
    }

    /** Como crearPaciente, pero devuelve la respuesta completa (expediente, nombres, apellidos). */
    private JsonNode crearPacienteConDatos(String token) throws Exception {
        String cuerpo = mockMvc.perform(post("/pacientes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"persona":{"dui":"%s","nombres":"Paciente","apellidos":"Del Caso %d",
                                            "fechaNacimiento":"1990-05-20","sexo":"F"},
                                 "tipoSangre":"O+"}
                                """.formatted(duiUnico(), siguiente())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return json.readTree(cuerpo);
    }

    private int medicamentosGuardados(long unaReceta, long otraReceta) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM prescripcion_medicamentos WHERE prescripcion_id IN (?, ?)",
                Integer.class, unaReceta, otraReceta);
        return total == null ? 0 : total;
    }
}
