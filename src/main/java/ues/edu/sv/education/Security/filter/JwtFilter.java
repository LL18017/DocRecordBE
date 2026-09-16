package ues.edu.sv.education.Security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ues.edu.sv.education.service.auth.JwtService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    /** Solo serializa los dos campos del cuerpo de error; no necesita configuracion. */
    private static final ObjectMapper JSON = new ObjectMapper();
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {

        String path = req.getRequestURI();

        //  Permitir rutas públicas
        // GET /especialidades es publico: lo consume el formulario de registro,
        // donde todavia no hay sesion. Sin esta linea el filtro rechaza antes
        // de que las reglas de autorizacion lleguen a permitirlo.
        if (path.equals("/especialidades") && "GET".equalsIgnoreCase(req.getMethod())) {
            chain.doFilter(req, res);
            return;
        }

        if (path.startsWith("/auth/") || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-resources")
                || path.startsWith("/webjars")) {
            chain.doFilter(req, res);
            return;
        }
        //extraer el token
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            noAutenticado(res, "No hay una sesion activa. Inicia sesion para continuar.");
            return;
        }

        String token = header.substring(7);
        try {
            Claims claims = jwtService.getClaims(token);

            // Extraer id del token
            String usuarioId = claims.getId();
            List<String> rawAuthorities = claims.get("authorities", List.class);

            List<GrantedAuthority> authorities = rawAuthorities == null
                    ? List.of()
                    : rawAuthorities.stream()
                    .map(SimpleGrantedAuthority::new)   // String → GrantedAuthority
                    .collect(Collectors.toList());
            if (usuarioId == null) {
                noAutenticado(res, "Tu sesion no es valida. Inicia sesion de nuevo.");
                return;
            }

            // Guardar en request
            req.setAttribute("usuarioId", usuarioId);

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(usuarioId,null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authToken);

        } catch (Exception e) {
            // A nivel DEBUG y no por System.out: el motivo exacto -firma que no
            // cuadra, token vencido, JSON corrupto- sirve para diagnosticar,
            // pero escribirlo en cada peticion llena el registro del servidor
            // publico y no le dice nada a quien lo lee.
            logger.debug("Token rechazado: " + e.getMessage());
            noAutenticado(res, "Tu sesion expiro o no es valida. Inicia sesion de nuevo.");
            return;
        }

        // Continuar con la cadena
        chain.doFilter(req, res);
    }

    /**
     * Responde un 401 con el MISMO formato que el resto de la API.
     *
     * ── Por que no basta con `res.getWriter().write(texto)` ───────────────
     * Es lo que habia antes, y dejaba al filtro fuera del contrato de TT-01:
     * el manejador global responde siempre `{"error": ..., "message": ...}` en
     * application/json, pero estas tres salidas escribian texto plano sin
     * Content-Type. El cliente que sabe leer los errores de la API se
     * encontraba con un cuerpo que no era JSON justo en el caso mas comun
     * -sesion vencida- y caia en su mensaje generico.
     *
     * Ocurre aqui y no en el GlobalExceptionHandler porque un filtro corre
     * ANTES del DispatcherServlet: lanzar una excepcion desde este punto no la
     * ve ningun @ExceptionHandler.
     *
     * ── Por que el mensaje esta redactado para una persona ────────────────
     * Porque la interfaz lo muestra tal cual: `extraerMensajeDeError` en
     * lib/api.ts se queda con `message` si viene. "Debe enviar token Bearer"
     * describia la cabecera que falta, que es cierto y no le sirve de nada a
     * quien solo ve que lo sacaron del sistema.
     */
    private void noAutenticado(HttpServletResponse res, String mensaje) throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // Serializado, no concatenado: una comilla o un salto de linea en el
        // mensaje romperia el JSON a mano y el cliente recibiria basura
        // justo cuando intenta explicar un error.
        JSON.writeValue(res.getWriter(), Map.of(
                "error", "No autenticado",
                "message", mensaje));
    }
}
