package ues.edu.sv.education;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DEF-01: V41 siembra los tipos de cita y de habito que agendar (HU-30) y
 * registrar habitos (HU-14) exigen. La base de pruebas se migra con Flyway, asi
 * que sin la migracion los dos listados llegarian vacios.
 */
class CatalogosInicialesIT extends PruebaClinica {

    private static final List<String> TIPOS_CITA = List.of(
            "Consulta general", "Control", "Primera vez",
            "Seguimiento de crónicos", "Resultados de laboratorio");

    private static final List<String> TIPOS_HABITO = List.of(
            "Tabaquismo", "Consumo de alcohol", "Actividad física",
            "Alimentación", "Sueño");

    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("HU-30: el listado de tipos de cita trae los valores iniciales")
    void tiposDeCitaIniciales() throws Exception {
        Set<String> nombres = nombres(
                "/tipos-cita", "nombre");
        assertTrue(nombres.containsAll(TIPOS_CITA),
                "Faltan tipos de cita iniciales. Hay: " + nombres);
    }

    @Test
    @DisplayName("HU-14: el listado de tipos de hábito trae los valores iniciales")
    void tiposDeHabitoIniciales() throws Exception {
        Set<String> descripciones = nombres(
                "/api/habitos-estilo-de-vida/tipos", "descripcion");
        assertTrue(descripciones.containsAll(TIPOS_HABITO),
                "Faltan tipos de hábito iniciales. Hay: " + descripciones);
    }

    @Test
    @DisplayName("HU-30/HU-14: los valores iniciales no están duplicados")
    void sinDuplicados() {
        for (String nombre : TIPOS_CITA) {
            Integer total = jdbc.queryForObject(
                    "SELECT count(*) FROM public.tipos_cita WHERE lower(nombre) = lower(?)",
                    Integer.class, nombre);
            assertEquals(1, total, "Tipo de cita repetido: " + nombre);
        }
        for (String descripcion : TIPOS_HABITO) {
            Integer total = jdbc.queryForObject(
                    "SELECT count(*) FROM public.tipos_habito WHERE lower(descripcion) = lower(?)",
                    Integer.class, descripcion);
            assertEquals(1, total, "Tipo de hábito repetido: " + descripcion);
        }
    }

    private Set<String> nombres(String ruta, String campo) throws Exception {
        JsonNode listado = json.readTree(
                mockMvc.perform(get(ruta)
                                .header("Authorization", bearer(tokenDeAdministrador())))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        Set<String> resultado = new HashSet<>();
        listado.forEach(n -> resultado.add(n.get(campo).asText()));
        return resultado;
    }
}
