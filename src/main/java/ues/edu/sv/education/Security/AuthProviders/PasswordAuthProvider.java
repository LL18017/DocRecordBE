package ues.edu.sv.education.Security.AuthProviders;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import ues.edu.sv.education.controller.error.CustomAuthenticationException;
import ues.edu.sv.education.model.dto.auth.CustomUserDetails;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.service.auth.UserAuthService;

import jakarta.persistence.EntityNotFoundException;

import java.util.Objects;

public class PasswordAuthProvider implements AuthenticationProvider {

    private final PasswordEncoder encoder;
    private final UserAuthService userAuthService;

    public PasswordAuthProvider(PasswordEncoder encoder, UserAuthService userAuthService) {
        this.encoder = encoder;
        this.userAuthService = userAuthService;
    }

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {

        String email = authentication.getName();
        String rawPassword = Objects.requireNonNull(authentication.getCredentials()).toString();

        // HU-01, criterio 2: una contrasena incorrecta responde 401 con
        // "Credenciales incorrectas". Un correo que NO existe tiene que
        // responder exactamente lo mismo.
        //
        // ── Que hacia antes ──────────────────────────────────────────────
        // `getUser` no devuelve null: lanza EntityNotFoundException, que el
        // manejador global convierte en 404 "No se encontro al usuario". Es
        // decir, el `if (user == null)` de abajo era codigo muerto y el login
        // contestaba DISTINTO segun el correo existiera o no: 404 con un texto
        // para uno, 401 con otro texto para el otro.
        //
        // ── Por que importa ──────────────────────────────────────────────
        // Con esa diferencia, cualquiera puede averiguar que correos tienen
        // cuenta en el sistema probandolos uno a uno, sin adivinar ni una sola
        // contrasena. En un expediente clinico la lista de usuarios no es un
        // dato neutro: dice quien trabaja aqui, y con los correos
        // institucionales dice ademas quien es paciente.
        //
        // El mismo motivo por el que HU-03 criterio 1 obliga a que la
        // recuperacion conteste siempre igual "exista o no la cuenta". Esta es
        // la otra puerta por la que se preguntaba lo mismo.
        User user;
        try {
            user = userAuthService.getUser(email);
        } catch (EntityNotFoundException noExiste) {
            throw new CustomAuthenticationException("Credenciales incorrectas", 401);
        }

        UserDetails userDetails = new CustomUserDetails(user);

        if (!userDetails.isEnabled())
            throw new CustomAuthenticationException("Usuario no ha confirmado su cuenta aun", 401) {
            };

        if (!userDetails.isAccountNonExpired())
            throw new CustomAuthenticationException("Cuenta expirada",401);

        if (!userDetails.isAccountNonLocked())
            throw new CustomAuthenticationException("Usuario bloqueado",401);

        if (!encoder.matches(rawPassword, userDetails.getPassword())) {
           throw new CustomAuthenticationException("Credenciales incorrectas",401);
        }

        return new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
