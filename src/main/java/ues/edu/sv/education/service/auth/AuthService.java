package ues.edu.sv.education.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ues.edu.sv.education.model.dto.User.UserRequestDto;
import ues.edu.sv.education.model.dto.auth.CustomUserDetails;
import ues.edu.sv.education.model.dto.auth.LoginResponseDto;
import ues.edu.sv.education.model.dto.auth.UserLoginDto;
import ues.edu.sv.education.model.dto.User.UserResponseDto;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.mappers.RoleMapper;
import ues.edu.sv.education.model.mappers.UserMapper;
import ues.edu.sv.education.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final UserAuthService service;
    private final JwtService jwtService;
    private final AuthenticationManager authManager;
    private final PasswordEncoder passwordEncoder;


    public LoginResponseDto loging(UserLoginDto loginDto) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginDto.email(),
                        loginDto.password()
                )
        );

        CustomUserDetails user =
                (CustomUserDetails) auth.getPrincipal();

        assert user != null;
        return new LoginResponseDto(
                auth.getName(),
                jwtService.generateToken(user),
                jwtService.generateRefreshToken(user),
                auth.getAuthorities().stream().map(a -> RoleMapper.toDto(a.getAuthority())).toList()
        );
    }

    public LoginResponseDto refresh(String authHeader) {
        System.out.println(authHeader);
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
        CustomUserDetails user = new CustomUserDetails(u);
        return new LoginResponseDto( u.getName(), jwtService.generateToken(user), jwtService.generateRefreshToken(user),
                user.getAuthorities().stream().map(a -> RoleMapper.toDto(a.getAuthority())).toList()
        );
    }

    public User createUser(UserRequestDto user) {
        User userToSave= UserMapper.toEntity(user);
        userToSave.setPassword(passwordEncoder.encode(userToSave.getPassword()));
        return this.userRepository.save(userToSave);
    }
}
