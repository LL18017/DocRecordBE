package ues.edu.sv.education;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TT-04, criterio 4: la configuracion del repositorio no trae secretos reales.
 *
 * Lee application.properties tal cual (sin resolver variables), porque lo que
 * importa es lo que esta ESCRITO en el repositorio: un valor por defecto
 * detras de `${VAR:...}` es un secreto publicado igual que uno sin variable.
 * No necesita contexto de Spring ni base de datos.
 */
class SinCredencialesEnLaConfiguracionIT {

    private Properties cargar(String recurso) throws Exception {
        Properties propiedades = new Properties();
        try (var entrada = new ClassPathResource(recurso).getInputStream()) {
            propiedades.load(new java.io.InputStreamReader(entrada, StandardCharsets.UTF_8));
        }
        return propiedades;
    }

    @Test
    @DisplayName("MAIL_PASSWORD no tiene valor por defecto")
    void laContrasenaDelCorreoNoTieneValorPorDefecto() throws Exception {
        assertEquals("${MAIL_PASSWORD:}", cargar("application.properties").getProperty("spring.mail.password"));
    }

    @Test
    @DisplayName("JWT_SECRET no tiene valor por defecto en la configuracion base")
    void laClaveJwtNoTieneValorPorDefecto() throws Exception {
        assertEquals("${JWT_SECRET}", cargar("application.properties").getProperty("app.jwt.secret"));
    }

    @Test
    @DisplayName("la clave JWT de desarrollo vive solo en el perfil dev y esta marcada como tal")
    void laClaveDeDesarrolloEstaEnElPerfilDev() throws Exception {
        String valor = cargar("application-dev.properties").getProperty("app.jwt.secret");

        // Decodificada dice SOLO-DESARROLLO...: no se puede confundir con una real.
        String clave = valor.substring("${JWT_SECRET:".length(), valor.length() - 1);
        String decodificada = new String(java.util.Base64.getUrlDecoder().decode(clave), StandardCharsets.UTF_8);
        assertTrue(decodificada.startsWith("SOLO-DESARROLLO"), "la clave de desarrollo debe decir que lo es: " + decodificada);
    }

    @Test
    @DisplayName("el origen de CORS no es un comodin")
    void corsNoAceptaComodin() throws Exception {
        String origenes = cargar("application.properties").getProperty("app.cors.origenes");
        assertFalse(origenes.contains("*"), "la lista de origenes CORS no debe contener comodines: " + origenes);
    }
}
