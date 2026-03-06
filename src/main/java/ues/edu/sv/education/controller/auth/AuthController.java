package ues.edu.sv.education.controller.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.dto.auth.UserLoginDto;
import ues.edu.sv.education.dto.auth.UserResponseDto;
import ues.edu.sv.education.entity.User;
import ues.edu.sv.education.service.auth.AuthService;
import ues.edu.sv.education.service.auth.UserService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService service;
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<UserResponseDto> getUser(@Valid @RequestBody UserLoginDto user) {

        return ResponseEntity.ok(authService.loging(user));
    }
    @PostMapping("/refresh")
    public ResponseEntity<UserResponseDto> refresh(@RequestHeader(HttpHeaders.AUTHORIZATION) String token) {
        return  ResponseEntity.ok(authService.refresh(token));
    }

    @PostMapping
    public ResponseEntity<User> createUser(@Valid @RequestBody User user){
        return ResponseEntity.ok(authService.createUser(user));
    }
}
