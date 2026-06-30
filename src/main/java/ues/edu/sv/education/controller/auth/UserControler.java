package ues.edu.sv.education.controller.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ues.edu.sv.education.model.dto.User.UserRequestDto;
import ues.edu.sv.education.model.dto.User.UserResponseDto;
import ues.edu.sv.education.model.entity.User;
import ues.edu.sv.education.service.auth.UserAuthService;
import ues.edu.sv.education.service.user.UserService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserControler {
    private  final UserAuthService service;
    private final UserService userService;


    @GetMapping
    public List<User> getUsers(){
        return service.getAllUser();
    }


    @GetMapping("/all")
    public ResponseEntity<List<UserResponseDto>> getAll(
            @RequestParam(defaultValue = "0") int inicio,
            @RequestParam(defaultValue = "20") int fin
    ) {
        return ResponseEntity.ok(userService.getAll(inicio,fin));
    }
    @PostMapping("/{userId}/role/{roleId}")
    public ResponseEntity<UserResponseDto> addRole(
            @PathVariable(required = true) int userId,
            @PathVariable(required = true) int roleId
    ) {
        return ResponseEntity.ok(userService.addRole(userId,roleId));
    }
    @PostMapping()
    public ResponseEntity<UserResponseDto> createRole(
            @RequestBody  UserRequestDto userRequestDto
    ) {
        return ResponseEntity.ok(userService.createUser(userRequestDto));
    }
}
