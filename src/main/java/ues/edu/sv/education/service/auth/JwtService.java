package ues.edu.sv.education.service.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import ues.edu.sv.education.model.dto.auth.CustomUserDetails;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JwtService {
    // Los tres valores vienen de application.properties (app.jwt.*), que a su vez
    // los toma de variables de entorno. Antes estaban escritos aqui, es decir,
    // publicados en el repositorio.
    // Nota: no son final porque @RequiredArgsConstructor incluiria los campos
    // final sin inicializar en el constructor generado y romperia la inyeccion.
    @Value("${app.jwt.secret}")
    private String secretKey;

    @Value("${app.jwt.expiration-ms}")
    private long expirationTime;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationTime;

    public String generateToken(@Valid @RequestBody CustomUserDetails user) {
        return buildToken(user, expirationTime);
    }

    public String generateRefreshToken(@Valid @RequestBody CustomUserDetails user) {
        return buildToken(user, refreshExpirationTime);
    }

    //genera un toke dado un usuario y un tiempo
    public String buildToken(@Valid CustomUserDetails user, long Expiration) {
        List<String> authorities = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)  // "ROLE_ADMIN" → String
                .toList();
        return Jwts.builder()
                .id(user.getUser().getUserID().toString())
                .claims(Map.of(
                        "name", user.getUser().getPersona().getNombres() + " " + user.getUser().getPersona().getApellidos(),
                        "authorities", authorities
                ))
                .subject(user.getUser().getEmail())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + Expiration))
                .signWith(generateSecreteKey(secretKey))
                .compact();
    }

    public String extractUserName(String token) {
        return Jwts.parser().verifyWith((SecretKey) generateSecreteKey(secretKey)).build().parseSignedClaims(token).getPayload().getSubject();
    }


    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith((SecretKey) generateSecreteKey(secretKey))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenExpired(String token) {
        try {
            return getClaims(token).getExpiration().before(new Date());
        } catch ( Exception e) {
            return true;
        }
    }

    private Key generateSecreteKey(String baseKey) {
        return Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(baseKey));
    }

}
