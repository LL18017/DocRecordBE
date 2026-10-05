package ues.edu.sv.education.Security;

import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Swagger: quien lo puede abrir (TT-03, criterio 4) y que dice de cada
 * endpoint (TT-03, criterio 2).
 *
 * ── Quien lo abre ──────────────────────────────────────────────────────────
 * En produccion Swagger no queda expuesto: publica el mapa completo de la API
 * -cada ruta, que recibe y que devuelve-, y eso le ahorra trabajo a cualquiera
 * que la quiera atacar. Pero tampoco se apaga, porque la tarea existe para que
 * el frontend y el evaluador prueben los endpoints sin leer el codigo. Por eso
 * se protege con usuario y clave (HTTP Basic), que se le entregan a quien lo
 * necesite.
 *
 *   app.swagger.publico=true   (por defecto, desarrollo) abierto.
 *   app.swagger.publico=false  con usuario y clave: pide credenciales.
 *                              sin ellos: cerrado del todo. Si alguien olvida
 *                              definirlos en el servidor, Swagger queda cerrado,
 *                              no abierto.
 *
 * docker-compose.prod.yml fija publico=false, asi que produccion no depende de
 * que nadie se acuerde de nada.
 *
 * Es una cadena de seguridad aparte, con @Order(1), porque estas credenciales
 * no son cuentas del sistema: no viven en `users`, no tienen rol clinico y no
 * sirven para la API. La cadena principal (BasicConfiguration) atiende todo lo
 * demas.
 *
 * ── Que dice de cada endpoint ──────────────────────────────────────────────
 * springdoc no lee @PreAuthorize, asi que Swagger no decia que rol pide cada
 * endpoint. rolesEnLaDocumentacion() lo agrega a la descripcion leyendo la
 * misma anotacion que aplica el permiso: si cambia el permiso, cambia la
 * documentacion, sin editarla a mano (criterio 3).
 */
@Configuration
public class ConfiguracionDeSwagger {

    static final String[] RUTAS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml"
    };

    @Bean
    @Order(1)
    SecurityFilterChain cadenaDeSwagger(
            HttpSecurity http,
            @Value("${app.swagger.publico:true}") boolean publico,
            @Value("${app.swagger.usuario:}") String usuario,
            @Value("${app.swagger.clave:}") String clave) throws Exception {

        http.securityMatcher(RUTAS)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (publico) {
            http.authorizeHttpRequests(a -> a.anyRequest().permitAll());
        } else if (usuario.isBlank() || clave.isBlank()) {
            http.authorizeHttpRequests(a -> a.anyRequest().denyAll());
        } else {
            http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
                    .httpBasic(b -> b.realmName("Documentacion de la API de DocRecord"))
                    .authenticationManager(new ProviderManager(new CredencialDeSwagger(usuario, clave)));
        }
        return http.build();
    }

    /**
     * Un unico usuario y clave, comparados en tiempo constante. No se cifra la
     * clave porque no se guarda en ninguna parte: llega por variable de entorno
     * al arrancar y vive solo en memoria.
     */
    private record CredencialDeSwagger(String usuario, String clave) implements AuthenticationProvider {

        @Override
        public Authentication authenticate(Authentication auth) {
            boolean usuarioOk = iguales(auth.getName(), usuario);
            boolean claveOk = iguales(String.valueOf(auth.getCredentials()), clave);
            if (usuarioOk && claveOk) {
                return UsernamePasswordAuthenticationToken.authenticated(
                        usuario, null, List.of(new SimpleGrantedAuthority("ROLE_DOCUMENTACION")));
            }
            throw new BadCredentialsException("Credenciales incorrectas");
        }

        @Override
        public boolean supports(Class<?> tipo) {
            return UsernamePasswordAuthenticationToken.class.isAssignableFrom(tipo);
        }

        private static boolean iguales(String a, String b) {
            return MessageDigest.isEqual(
                    a.getBytes(StandardCharsets.UTF_8),
                    b.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final Pattern ROL = Pattern.compile("'([A-Z_]+)'");

    @Bean
    OperationCustomizer rolesEnLaDocumentacion() {
        return (operacion, metodo) -> {
            String acceso;
            PreAuthorize permiso = permisoDe(metodo);
            if (permiso != null) {
                acceso = "**Acceso:** " + String.join(", ", rolesDe(permiso.value())) + ".";
            } else if (esPublico(metodo)) {
                acceso = "**Acceso:** público, sin token.";
            } else {
                acceso = "**Acceso:** cualquier usuario con sesión iniciada.";
            }

            String descripcion = operacion.getDescription();
            operacion.setDescription(descripcion == null || descripcion.isBlank()
                    ? acceso
                    : descripcion + "\n\n" + acceso);

            // El candado de Swagger, para que "Authorize" envie el token en
            // los endpoints que lo piden. Varios controladores ya lo declaran
            // con @SecurityRequirement; no se duplica.
            boolean yaLoPide = operacion.getSecurity() != null && operacion.getSecurity().stream()
                    .anyMatch(r -> r.containsKey("jwt"));
            if (!esPublico(metodo) && !yaLoPide) {
                operacion.addSecurityItem(new SecurityRequirement().addList("jwt"));
            }
            return operacion;
        };
    }

    // La anotacion del metodo manda sobre la de la clase, igual que en
    // @EnableMethodSecurity.
    private static PreAuthorize permisoDe(HandlerMethod metodo) {
        PreAuthorize delMetodo = AnnotatedElementUtils.findMergedAnnotation(metodo.getMethod(), PreAuthorize.class);
        return delMetodo != null
                ? delMetodo
                : AnnotatedElementUtils.findMergedAnnotation(metodo.getBeanType(), PreAuthorize.class);
    }

    private static List<String> rolesDe(String expresion) {
        Matcher m = ROL.matcher(expresion);
        List<String> roles = new java.util.ArrayList<>();
        while (m.find()) {
            roles.add(m.group(1));
        }
        return roles.isEmpty() ? List.of(expresion) : roles;
    }

    // Refleja los permitAll de BasicConfiguration: /auth/**, GET /especialidades
    // y GET /clinics/publicas (el mapa de la red, HU-28).
    private static boolean esPublico(HandlerMethod metodo) {
        RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(metodo.getBeanType(), RequestMapping.class);
        String ruta = base == null || base.value().length == 0 ? "" : base.value()[0];
        if (ruta.startsWith("/auth")) {
            return true;
        }
        boolean esLectura = metodo.hasMethodAnnotation(GetMapping.class);
        return esLectura && (ruta.equals("/especialidades") || ruta.equals("/clinics/publicas"));
    }
}
