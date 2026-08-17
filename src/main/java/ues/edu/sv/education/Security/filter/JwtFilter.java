package ues.edu.sv.education.Security.filter;

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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ues.edu.sv.education.service.auth.JwtService;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {

        String path = req.getRequestURI();

        //  Permitir rutas públicas
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
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.getWriter().write("Debe enviar token Bearer");
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
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.getWriter().write("Token no contiene id de usuario");
                return;
            }

            // Guardar en request
            req.setAttribute("usuarioId", usuarioId);

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(usuarioId,null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authToken);

            System.out.println(authorities);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.getWriter().write("Token inválido o expirado");
            return;
        }

        // Continuar con la cadena
        chain.doFilter(req, res);
    }
}
