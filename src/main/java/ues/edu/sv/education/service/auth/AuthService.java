package ues.edu.sv.education.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ues.edu.sv.education.dto.auth.CustomUserDetails;
import ues.edu.sv.education.dto.auth.UserLoginDto;
import ues.edu.sv.education.dto.auth.UserResponseDto;
import ues.edu.sv.education.entity.User;
import ues.edu.sv.education.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final UserService service;
    private final JwtService jwtService;
    private final AuthenticationManager authManager;
    private final PasswordEncoder passwordEncoder;


    public UserResponseDto loging(UserLoginDto loginDto) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginDto.email(),
                        loginDto.password()
                )
        );

        CustomUserDetails user =
                (CustomUserDetails) auth.getPrincipal();

        return new UserResponseDto(
                auth.getName(),
                jwtService.generateToken(user),
                jwtService.generateRefreshToken(user));
    }

    public UserResponseDto refresh(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Token invalido");
        }
        authHeader = authHeader.substring(7);
        String userEmail = jwtService.extractUserName(authHeader);
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("usuario invalido");
        }
        if (jwtService.isTokenExpired(authHeader)) {
            throw new IllegalArgumentException("fecha expirada");
        }
        User u = service.getUser(userEmail);
        CustomUserDetails user=new CustomUserDetails(u);
        return new UserResponseDto(u.getName(), jwtService.generateToken(user), jwtService.generateRefreshToken(user));
    }

    public User createUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return this.userRepository.save(user);
    }
}
