package ues.edu.sv.education.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import java.nio.charset.StandardCharsets;

/**
 * Segundo resolvedor de plantillas: el de TEXTO PLANO de los correos.
 *
 * Spring Boot ya registra uno por su cuenta, pero solo sabe de HTML
 * (src/main/resources/templates/**.html). Cada correo de DocRecord Sv se manda
 * en dos versiones -HTML y texto plano, ver EmailService- y la de texto tambien
 * es una plantilla, no una cadena concatenada en Java: si el texto viviera en
 * el codigo, tarde o temprano diria una cosa distinta del HTML.
 *
 * Como se reparten el trabajo los dos resolvedores, sin ambiguedad posible:
 *
 *   correo/texto/<nombre>  ->  templates/correo/texto/<nombre>.txt   (este, TEXT)
 *   correo/html/<nombre>   ->  templates/correo/html/<nombre>.html   (el de Boot)
 *
 * Este va primero (orden 1; el de Boot queda en 2 por
 * spring.thymeleaf.template-resolver-order). Con setCheckExistence(true) un
 * nombre que no tenga .txt no se lo queda: devuelve null y la peticion pasa al
 * siguiente resolvedor. Por eso las plantillas HTML llegan intactas al de Boot.
 */
@Configuration
public class ConfiguracionDePlantillasDeCorreo {

    @Bean
    public ITemplateResolver resolvedorDePlantillasDeTexto() {
        ClassLoaderTemplateResolver resolvedor = new ClassLoaderTemplateResolver();
        resolvedor.setPrefix("templates/");
        resolvedor.setSuffix(".txt");
        // TEXT y no HTML: en modo texto Thymeleaf no escapa entidades ni intenta
        // parsear etiquetas, asi que un "&" o un "<" del contenido salen tal cual.
        resolvedor.setTemplateMode(TemplateMode.TEXT);
        resolvedor.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolvedor.setCheckExistence(true);
        resolvedor.setCacheable(true);
        resolvedor.setOrder(1);
        return resolvedor;
    }
}
