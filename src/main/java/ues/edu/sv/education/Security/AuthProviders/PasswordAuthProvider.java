package ues.edu.sv.education.Security.AuthProviders;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import ues.edu.sv.education.dto.auth.CustomUserDetails;
import ues.edu.sv.education.entity.User;
import ues.edu.sv.education.service.auth.UserService;

import java.util.Objects;

public class PasswordAuthProvider implements AuthenticationProvider {

    private final PasswordEncoder encoder;
    private final UserService userService;

    public PasswordAuthProvider(PasswordEncoder encoder, UserService userService) {
        this.encoder = encoder;
        this.userService = userService;
    }

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {

        String email = authentication.getName();
        String rawPassword = Objects.requireNonNull(authentication.getCredentials()).toString();

        User user = userService.getUser(email);

        if (user == null) {
            throw new BadCredentialsException("Usuario no encontrado");
        }

        UserDetails userDetails = new CustomUserDetails(user);

        if (!userDetails.isEnabled())
            throw new DisabledException("Usuario deshabilitado");

        if (!userDetails.isAccountNonExpired())
            throw new DisabledException("Cuenta expirada");

        if (!userDetails.isAccountNonLocked())
            throw new DisabledException("Usuario bloqueado");

        if (!encoder.matches(rawPassword, userDetails.getPassword())) {
            throw new BadCredentialsException("Credenciales incorrectas");
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
