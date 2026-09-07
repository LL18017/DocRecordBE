package ues.edu.sv.education.Security.filter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ues.edu.sv.education.model.dto.auth.CustomUserDetails;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.repository.UserRepository;
import ues.edu.sv.education.service.auth.JwtService;

import java.io.IOException;

@RequiredArgsConstructor
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest req,
            HttpServletResponse res,
            FilterChain chain
    ) throws ServletException, IOException {

        String path = req.getRequestURI();

        // Rutas públicas
        if (path.startsWith("/auth/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-resources")
                || path.startsWith("/webjars")) {

            chain.doFilter(req, res);
            return;
        }

        // Extraer token
        String header = req.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.getWriter().write("Debe enviar token Bearer");
            return;
        }

        String token = header.substring(7);

        try {

            Claims claims = jwtService.getClaims(token);

            // ID almacenado en el JWT
            String usuarioId = claims.getId();

            if (usuarioId == null) {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.getWriter().write("Token no contiene id de usuario");
                return;
            }

            // Obtener usuario
            User user = userRepository.findById(Integer.valueOf(usuarioId))
                    .orElseThrow(() ->
                            new UsernameNotFoundException(
                                    "Usuario no encontrado"
                            )
                    );

            // Crear UserDetails
            CustomUserDetails userDetails =
                    new CustomUserDetails(user);

            // Usar las authorities del usuario
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            // Guardar usuario autenticado
            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authToken);

            // Opcional: guardar ID en request
            req.setAttribute("usuarioId", usuarioId);

        } catch (Exception e) {

            System.out.println(e.getMessage());

            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.getWriter().write("Token inválido o expirado");
            return;
        }

        chain.doFilter(req, res);
    }
}