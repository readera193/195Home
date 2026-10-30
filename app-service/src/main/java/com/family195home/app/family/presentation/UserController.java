package com.family195home.app.family.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.family195home.app.demo.DemoLoginRateLimiter;
import com.family195home.app.demo.DemoLoginService;
import com.family195home.app.family.application.UserService;
import com.family195home.app.family.presentation.dto.AuthConfigResponse;
import com.family195home.app.family.presentation.dto.LoginRequest;
import com.family195home.app.family.presentation.dto.LoginResponse;
import com.family195home.app.shared.ApiException;
import com.family195home.app.shared.ErrorKind;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;

@Tag(name = "認證", description = "LINE 登入與 Demo 體驗登入（無需 token）")
@RestController
@RequestMapping("/api/auth")
public class UserController {

    private final UserService userService;
    private final DemoLoginService demoLoginService;
    private final DemoLoginRateLimiter demoLoginRateLimiter;
    private final boolean demoLoginEnabled;

    public UserController(
            UserService userService,
            DemoLoginService demoLoginService,
            DemoLoginRateLimiter demoLoginRateLimiter,
            @Value("${app.demo-login.enabled:false}") boolean demoLoginEnabled) {
        this.userService = userService;
        this.demoLoginService = demoLoginService;
        this.demoLoginRateLimiter = demoLoginRateLimiter;
        this.demoLoginEnabled = demoLoginEnabled;
    }

    @SecurityRequirements
    @GetMapping("/config")
    public AuthConfigResponse config() {
        return new AuthConfigResponse(demoLoginEnabled);
    }

    @SecurityRequirements
    @PostMapping("/line")
    public ResponseEntity<LoginResponse> loginWithLine(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(toResponse(userService.loginWithLine(request.code())));
    }

    @Operation(summary = "Demo 體驗登入", description = "一鍵建立隨機帳號與示範資料並回傳 token；僅在 app.demo-login.enabled=true 時可用，每小時限流（每 IP 20 次）。")
    @SecurityRequirements
    @PostMapping("/demo")
    public ResponseEntity<LoginResponse> loginAsDemo(HttpServletRequest request) {
        if (!demoLoginEnabled) {
            throw new ApiException(ErrorKind.NOT_FOUND, "DEMO_LOGIN_DISABLED", "Demo 體驗未開啟");
        }
        demoLoginRateLimiter.acquire(request.getRemoteAddr());
        return ResponseEntity.ok(toResponse(demoLoginService.login()));
    }

    private static LoginResponse toResponse(UserService.LoginResult result) {
        return new LoginResponse(
                result.token(), result.userId(), result.displayName(), DateTimeFormatter.ISO_INSTANT.format(result.expiresAt()));
    }
}
