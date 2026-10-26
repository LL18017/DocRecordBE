package ues.edu.sv.education;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

/**
 * TT-04: CORS acepta solo la lista configurada de origenes, no cualquiera.
 *
 * Antes la API aceptaba CUALQUIER origen junto con credenciales
 * (`addAllowedOriginPattern("*")`): una pagina ajena podia llamarla desde el
 * navegador de un usuario con sesion. Lo que se prueba es la lista por defecto
 * de application.properties (app.cors.origenes), que son los origenes reales
 * del sistema: el frontend en desarrollo y los dos dominios publicos.
 */
class CorsRestringidoIT extends PruebaClinica {

    private static final String[] ORIGENES_PERMITIDOS = {
            "http://localhost:3000",
            "https://docrecordsv.duckdns.org",
            "https://api-docrecordsv.duckdns.org",
    };

    @Test
    @DisplayName("los origenes del sistema pasan el preflight y reciben las credenciales")
    void losOrigenesDelSistemaPasan() throws Exception {
        for (String origen : ORIGENES_PERMITIDOS) {
            var respuesta = mockMvc.perform(options("/pacientes")
                            .header("Origin", origen)
                            .header("Access-Control-Request-Method", "GET")
                            .header("Access-Control-Request-Headers", "authorization"))
                    .andReturn().getResponse();

            assertEquals(200, respuesta.getStatus(), origen + ": el preflight deberia pasar");
            // El origen se devuelve exacto, no "*": con credenciales el navegador
            // rechaza el comodin.
            assertEquals(origen, respuesta.getHeader("Access-Control-Allow-Origin"));
            assertEquals("true", respuesta.getHeader("Access-Control-Allow-Credentials"));
        }
    }

    @Test
    @DisplayName("un origen ajeno es rechazado en el preflight")
    void unOrigenAjenoEsRechazado() throws Exception {
        var respuesta = mockMvc.perform(options("/pacientes")
                        .header("Origin", "https://sitio-malicioso.example")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andReturn().getResponse();

        assertEquals(403, respuesta.getStatus());
        assertNull(respuesta.getHeader("Access-Control-Allow-Origin"),
                "no debe autorizarse un origen fuera de la lista");
    }

    @Test
    @DisplayName("una peticion real desde un origen ajeno no recibe cabeceras CORS")
    void unaPeticionDeUnOrigenAjenoNoRecibeCabeceras() throws Exception {
        var respuesta = mockMvc.perform(get("/especialidades")
                        .header("Origin", "https://sitio-malicioso.example"))
                .andReturn().getResponse();

        assertEquals(403, respuesta.getStatus());
        assertNull(respuesta.getHeader("Access-Control-Allow-Origin"));
        assertNull(respuesta.getHeader("Access-Control-Allow-Credentials"));
    }

    @Test
    @DisplayName("un origen parecido a uno permitido tampoco pasa")
    void unOrigenParecidoNoPasa() throws Exception {
        for (String parecido : new String[]{
                "http://localhost:3001",
                "https://docrecordsv.duckdns.org.otro.example",
                "http://docrecordsv.duckdns.org",
        }) {
            var respuesta = mockMvc.perform(options("/pacientes")
                            .header("Origin", parecido)
                            .header("Access-Control-Request-Method", "GET"))
                    .andReturn().getResponse();

            assertEquals(403, respuesta.getStatus(), parecido + " no esta en la lista");
        }
    }
}
