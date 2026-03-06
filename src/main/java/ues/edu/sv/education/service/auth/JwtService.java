package ues.edu.sv.education.service.auth;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import ues.edu.sv.education.dto.auth.CustomUserDetails;
import ues.edu.sv.education.entity.User;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JwtService {
    final PasswordEncoder passwordEncoder;
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
        return Jwts.builder()
                .id(user.getUser().getUserID().toString())
                .claims(Map.of("name", user.getUser().getName()))
                .subject(user.getUser().getEmail())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + Expiration))
                .signWith(generateSecreteKey(SECRET_KEY))
                .compact();
    }

    public String extractUserName(String token) {
       return  Jwts.parser().verifyWith((SecretKey) generateSecreteKey(SECRET_KEY)).build().parseSignedClaims(token).getPayload().getSubject();
    }

    private Key generateSecreteKey(String baseKey) {
        return Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(baseKey));
    }

    public boolean isTokenExpired(String token) {
        try {
            // Quitar prefijo Bearer si existe
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            Date expiration = Jwts.parser()
                    .verifyWith((SecretKey) generateSecreteKey(SECRET_KEY))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getExpiration();

            // Retorna true si expiró
            return expiration.before(new Date());

        } catch (ExpiredJwtException e) {
            // El token ya expiró
            return true;
        } catch (JwtException e) {
            // Token inválido, malformado o con firma incorrecta
            return true;
        } catch (Exception e) {
            return true;
        }
    }
}
