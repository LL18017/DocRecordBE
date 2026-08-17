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

        User user = userAuthService.getUser(email);

        if (user == null) {
            throw new BadCredentialsException("Usuario no encontrado");
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
