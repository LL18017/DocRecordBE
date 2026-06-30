package ues.edu.sv.education.service.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    final String SECRET_KEY = "Pp66ApU5jFTa0Inys7eKQVGGUorowMahapH74X1Ho9W";
    final Integer EXPIRATION_TIME = 5 * 60 * 1000;
    final Integer REFRESH_EXPIRATION_TIME = 30 * 60 * 1000;

    public String generateToken(@Valid @RequestBody CustomUserDetails user) {
        return buildToken(user, EXPIRATION_TIME);
    }

    public String generateRefreshToken(@Valid @RequestBody CustomUserDetails user) {
        return buildToken(user, REFRESH_EXPIRATION_TIME);
    }

    //genera un toke dado un usuario y un tiempo
    public String buildToken(@Valid CustomUserDetails user, Integer Expiration) {
        List<String> authorities = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)  // "ROLE_ADMIN" → String
                .toList();
        return Jwts.builder()
                .id(user.getUser().getUserID().toString())
                .claims(Map.of(
                        "name", user.getUser().getName(),
                        "authorities", authorities
                ))
                .subject(user.getUser().getEmail())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + Expiration))
                .signWith(generateSecreteKey(SECRET_KEY))
                .compact();
    }

    public String extractUserName(String token) {
        return Jwts.parser().verifyWith((SecretKey) generateSecreteKey(SECRET_KEY)).build().parseSignedClaims(token).getPayload().getSubject();
    }


    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith((SecretKey) generateSecreteKey(SECRET_KEY))
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
