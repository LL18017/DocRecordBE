package ues.edu.sv.education.controller.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.auth.LoginResponseDto;
import ues.edu.sv.education.model.dto.auth.UserLoginDto;
import ues.edu.sv.education.model.dto.User.UserRequestDto;
import ues.edu.sv.education.model.dto.User.UserResponseDto;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.service.auth.AuthService;
import ues.edu.sv.education.service.auth.UserAuthService;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserAuthService service;
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> getUser(@Valid @RequestBody UserLoginDto user) {

        return ResponseEntity.ok(authService.loging(user));
    }
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDto> refresh(@RequestHeader(HttpHeaders.AUTHORIZATION) String token) {
        return  ResponseEntity.ok(authService.refresh(token));
    }

    @PostMapping("/register")
    public ResponseEntity<User> createUser(@Valid @RequestBody UserRequestDto user){
        return ResponseEntity.ok(authService.createUser(user));
    }
}
