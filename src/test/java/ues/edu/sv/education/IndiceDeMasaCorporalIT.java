package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-17 (DRS-88) · Peso, talla e índice de masa corporal.
 *
 * El criterio que de verdad importa aquí es el 2, y la propia historia explica
 * por qué:
 *
 *   "la tabla de IMC de adultos aplicada a un niño de 8 años da una
 *    clasificación sin sentido"
 *
 * Un niño de 8 años con IMC 16 está sano, y la tabla de adultos lo llamaría
 * «bajo peso». Una etiqueta equivocada en un expediente clínico orienta
 * decisiones, así que en menores el número se calcula pero la clasificación
 * dice que hay que leerla por percentiles.
 */
class IndiceDeMasaCorporalIT extends PruebaClinica {

    /** Da de alta un paciente con la fecha de nacimiento que haga falta. */
    private long crearPacienteNacidoEn(String token, LocalDate nacimiento) throws Exception {
        String cuerpo = mockMvc.perform(post("/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"persona":{"dui":"%s","nombres":"Paciente","apellidos":"Del IMC %d",
                                            "fechaNacimiento":"%s","sexo":"F"},
                                 "tipoSangre":"O+"}
                                """.formatted(duiUnico(), siguiente(), nacimiento)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("personaId").asLong();
    }

    private JsonNode tomar(long pacienteId, String medidas) throws Exception {
        String cuerpo = mockMvc.perform(post("/signos-vitales")
                        .header("Authorization", "Bearer " + tokenDeEnfermera())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d%s}".formatted(pacienteId, medidas)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private void rechazaLaToma(long pacienteId, String medidas) throws Exception {
        mockMvc.perform(post("/signos-vitales")
                        .header("Authorization", "Bearer " + tokenDeEnfermera())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":%d%s}".formatted(pacienteId, medidas)))
                .andExpect(status().isBadRequest());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 1 · el IMC con un decimal y su clasificación
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("el IMC se calcula con un decimal y trae la clasificación de la OMS")
    void calculaElImcYLoClasifica() throws Exception {
        String token = tokenDeMedico();
        long adulto = crearPacienteNacidoEn(token, LocalDate.now().minusYears(35));

        // 70 kg y 1.75 m -> 70 / (1.75 * 1.75) = 22.857... -> 22.9
        JsonNode toma = tomar(adulto, ",\"pesoKg\":70.0,\"estaturaCm\":175.0");

        assertEquals("22.9", toma.get("imc").asText(),
                "el IMC se redondea a un decimal");
        assertEquals("Normal", toma.get("clasificacionImc").asText());
    }

    @Test
    @DisplayName("la clasificación cubre toda la escala de la OMS")
    void toda_la_escala() throws Exception {
        String token = tokenDeMedico();

        // Misma estatura, pesos que caen en cada tramo.
        record Caso(String peso, String esperado) {}
        Caso[] casos = {
                new Caso("50.0", "Bajo peso"),        // 17.3
                new Caso("70.0", "Normal"),           // 22.9
                new Caso("85.0", "Sobrepeso"),        // 27.8
                new Caso("95.0", "Obesidad grado I"), // 31.0
                new Caso("110.0", "Obesidad grado II"),  // 35.9
                new Caso("130.0", "Obesidad grado III"), // 42.4
        };

        for (Caso caso : casos) {
            long adulto = crearPacienteNacidoEn(token, LocalDate.now().minusYears(40));
            JsonNode toma = tomar(adulto, ",\"pesoKg\":%s,\"estaturaCm\":175.0".formatted(caso.peso()));
            assertEquals(caso.esperado(), toma.get("clasificacionImc").asText(),
                    "con " + caso.peso() + " kg y 175 cm el IMC es " + toma.get("imc").asText());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 2 · en menores la tabla de adultos no aplica
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("en un niño se calcula el IMC pero NO se le pone la etiqueta de adultos")
    void enMenoresNoAplicaLaTablaDeAdultos() throws Exception {
        String token = tokenDeMedico();
        long nino = crearPacienteNacidoEn(token, LocalDate.now().minusYears(8));

        // 25 kg y 1.25 m -> IMC 16.0. En la tabla de adultos seria "Bajo peso",
        // y para un nino de 8 anos ese numero es perfectamente normal.
        JsonNode toma = tomar(nino, ",\"pesoKg\":25.0,\"estaturaCm\":125.0");

        assertEquals("16.0", toma.get("imc").asText(), "el numero si se calcula");

        String clasificacion = toma.get("clasificacionImc").asText();
        assertTrue(clasificacion.contains("percentiles"),
                "debe remitir a percentiles, y dijo: " + clasificacion);
        assertTrue(clasificacion.contains("menores"),
                "y explicar por que no aplica, y dijo: " + clasificacion);
        // Lo que NO debe decir bajo ningun concepto.
        assertTrue(!clasificacion.equals("Bajo peso") && !clasificacion.equals("Normal"),
                "no debe etiquetar a un nino con la tabla de adultos");
    }

    @Test
    @DisplayName("a los 18 recién cumplidos ya aplica la tabla de adultos")
    void alCumplirDieciochoSiAplica() throws Exception {
        // El limite es el que dice el criterio, no "alrededor de".
        String token = tokenDeMedico();
        long reciénAdulto = crearPacienteNacidoEn(token, LocalDate.now().minusYears(18).minusDays(1));

        JsonNode toma = tomar(reciénAdulto, ",\"pesoKg\":70.0,\"estaturaCm\":175.0");

        assertEquals("Normal", toma.get("clasificacionImc").asText());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterio 3 · los rangos
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("un peso o una talla fuera de rango se rechazan")
    void rangosDePesoYTalla() throws Exception {
        String token = tokenDeMedico();
        long paciente = crearPacienteNacidoEn(token, LocalDate.now().minusYears(30));

        // El caso real no es teclear un absurdo: es correr la coma de sitio.
        rechazaLaToma(paciente, ",\"pesoKg\":0.2");    // 0.2 kg
        rechazaLaToma(paciente, ",\"pesoKg\":450.0");  // 450 kg
        rechazaLaToma(paciente, ",\"estaturaCm\":25.0");   // 0.25 m
        rechazaLaToma(paciente, ",\"estaturaCm\":260.0");  // 2.60 m
    }

    @Test
    @DisplayName("los límites del rango sí se aceptan, incluido el prematuro")
    void losLimitesSonValidos() throws Exception {
        // 0.5 kg y 30 cm parecen imposibles hasta que se recuerda que un
        // prematuro extremo pesa menos de un kilo. Un rango "razonable para un
        // adulto" dejaria fuera a los pacientes mas fragiles del sistema.
        String token = tokenDeMedico();
        long prematuro = crearPacienteNacidoEn(token, LocalDate.now().minusDays(20));

        JsonNode toma = tomar(prematuro, ",\"pesoKg\":0.5,\"estaturaCm\":30.0");
        assertTrue(toma.get("imc").asText().startsWith("5."),
                "0.5 kg en 0.3 m da 5.6; se calcula igual: " + toma.get("imc").asText());
        assertTrue(toma.get("clasificacionImc").asText().contains("percentiles"));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sin los dos datos no hay IMC
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("sin peso o sin talla, el IMC viene null en vez de inventado")
    void sinLosDosDatosNoHayImc() throws Exception {
        String token = tokenDeMedico();
        long paciente = crearPacienteNacidoEn(token, LocalDate.now().minusYears(30));

        JsonNode soloPeso = tomar(paciente, ",\"pesoKg\":70.0");
        assertTrue(soloPeso.get("imc").isNull(), "un IMC sin talla no es aproximado, es inventado");
        assertTrue(soloPeso.get("clasificacionImc").isNull());

        JsonNode soloTalla = tomar(paciente, ",\"estaturaCm\":175.0");
        assertTrue(soloTalla.get("imc").isNull());
    }
}
