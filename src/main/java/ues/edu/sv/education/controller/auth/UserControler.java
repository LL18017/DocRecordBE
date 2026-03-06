package ues.edu.sv.education.controller.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.dto.auth.UserLoginDto;
import ues.edu.sv.education.dto.auth.UserResponseDto;
import ues.edu.sv.education.entity.User;
import ues.edu.sv.education.service.auth.AuthService;
import ues.edu.sv.education.service.auth.UserService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserControler {
    private  final UserService service;



    @GetMapping
    public List<User> getUsers(){
        return service.getAllUser();
    }

}
