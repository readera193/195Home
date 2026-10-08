package com.family195home.app.family.presentation;

import com.family195home.app.family.presentation.dto.LoginRequest;
import com.family195home.app.family.presentation.dto.LoginResponse;
import com.family195home.app.family.presentation.dto.RegisterRequest;
import com.family195home.app.family.domain.User;
import com.family195home.app.family.presentation.dto.RegisterResponse;
import com.family195home.app.family.application.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request.email(), request.password());
        RegisterResponse response = new RegisterResponse(user.getId(), user.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        UserService.LoginResult result = userService.login(request.email(), request.password());
        LoginResponse response = new LoginResponse(
                result.token(), result.userId(), result.email(), DateTimeFormatter.ISO_INSTANT.format(result.expiresAt()));
        return ResponseEntity.ok(response);
    }
}
