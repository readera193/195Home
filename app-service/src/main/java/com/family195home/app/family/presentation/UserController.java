package com.family195home.app.family.presentation;

import com.family195home.app.family.application.UserService;
import com.family195home.app.family.presentation.dto.LoginRequest;
import com.family195home.app.family.presentation.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/line")
    public ResponseEntity<LoginResponse> loginWithLine(@Valid @RequestBody LoginRequest request) {
        UserService.LoginResult result = userService.loginWithLine(request.code());
        LoginResponse response = new LoginResponse(
                result.token(), result.userId(), result.displayName(), DateTimeFormatter.ISO_INSTANT.format(result.expiresAt()));
        return ResponseEntity.ok(response);
    }
}
