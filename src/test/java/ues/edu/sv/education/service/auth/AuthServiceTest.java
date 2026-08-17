/*
package ues.edu.sv.education.service.auth;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import ues.edu.sv.education.model.dto.User.UserRequestDto;
import ues.edu.sv.education.model.dto.auth.CustomUserDetails;
import ues.edu.sv.education.model.dto.auth.LoginResponseDto;
import ues.edu.sv.education.model.dto.auth.UserLoginDto;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.model.mappers.UserMapper;
import ues.edu.sv.education.repository.UserRepository;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    UserRequestDto userReq;
    UserLoginDto userLogin;
    User userSaved;
    String mail = "example@mail.com";
    String userName = "exampleUser";
    String password = "12345";
    String encodePassword = "encodePassword";
    String JWT="JWT";
    String refresh="refresh";
    List<Integer> roles = List.of(1);
    //mocks
    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAuthService service;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authManager;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setInfo() {
        userReq = new UserRequestDto(mail, userName, password, roles,1);
        userSaved = UserMapper.toEntity(userReq);
        userLogin = new UserLoginDto(mail, password);
    }

    @Test
    void shouldLoginSuccessfully() {
        // Mock del usuario autenticado
        CustomUserDetails userDetails = Mockito.mock(CustomUserDetails.class);
        // Mock del Authentication
        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.getPrincipal())
                .thenReturn(userDetails);
        Mockito.when(authentication.getName())
                .thenReturn(mail);
        Mockito.when(authentication.getAuthorities())
                .thenReturn(List.of());
        // AuthenticationManager
        Mockito.when(authManager.authenticate(Mockito.any(Authentication.class)))
                .thenReturn(authentication);
        // JWT
        Mockito.when(jwtService.generateToken(userDetails))
                .thenReturn(JWT);
        Mockito.when(jwtService.generateRefreshToken(userDetails))
                .thenReturn(refresh);
        LoginResponseDto response = authService.loging(userLogin);
        Assertions.assertEquals(JWT, response.token());
    }

    @Test
    void shouldRefreshTokenSuccessfully() {
        Mockito.when(jwtService.extractUserName(Mockito.anyString()))
                .thenReturn(mail);
        Mockito.when(jwtService.isTokenExpired(Mockito.anyString()))
                .thenReturn(false);
        Mockito.when(service.getUser(mail))
                .thenReturn(userSaved);
        Mockito.when(jwtService.generateToken(Mockito.any(CustomUserDetails.class)))
                .thenReturn(JWT);
        Mockito.when(jwtService.generateRefreshToken(Mockito.any(CustomUserDetails.class)))
                .thenReturn(refresh);
        LoginResponseDto response =
                authService.refresh("Bearer " + refresh);
        Assertions.assertEquals(JWT, response.token());
    }

    @Test
    void shouldThrowExceptionWhenTokenPrefixIsInvalid() {
        Assertions.assertThrows(IllegalArgumentException.class,()->{
                authService.refresh("Bearerfalse " + refresh);
        });
    }
    @Test
    void shouldThrowExceptionWhenUserDoesNotExistInRefreshToken() {
        Mockito.when(jwtService.extractUserName(Mockito.anyString()))
                .thenReturn(null);
        Assertions.assertThrows(IllegalArgumentException.class,()->{
            authService.refresh("Bearer " + refresh);
        });
    }
    @Test
    void shouldThrowExceptionWhenRefreshTokenIsExpired() {
        Mockito.when(jwtService.extractUserName(Mockito.anyString()))
                .thenReturn(mail);
        Mockito.when(jwtService.isTokenExpired(Mockito.anyString()))
                .thenReturn(true);
        Assertions.assertThrows(IllegalArgumentException.class,()->{
            authService.refresh("Bearer " + refresh);
        });
    }
    @Test
    void ShouldcreateUserSuccessfully() {
        Mockito.when(this.passwordEncoder.encode(password)).thenReturn(encodePassword);
        Mockito.when(userRepository.save(Mockito.any(User.class))).thenReturn(userSaved);
        User userExp = this.authService.createUser(userReq);
        Assertions.assertEquals(userExp, userSaved);
    }
}*/
