package com.family195home.app.family.service;

import com.family195home.app.common.ApiException;
import com.family195home.app.family.application.UserRepository;
import com.family195home.app.family.domain.User;
import com.family195home.app.family.dto.LoginResponse;
import com.family195home.app.family.dto.RegisterResponse;
import com.family195home.app.security.JwtTokenProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    // FR-024: Email 全系統唯一，重複註冊拒絕
    public RegisterResponse register(String email, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "此 Email 已被註冊，請改用登入");
        }
        User user = new User(email, passwordEncoder.encode(rawPassword), LocalDateTime.now());
        userRepository.save(user);
        return new RegisterResponse(user.getId(), user.getEmail());
    }

    public LoginResponse login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "帳號或密碼錯誤"));
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "帳號或密碼錯誤");
        }
        JwtTokenProvider.GeneratedToken generated = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        String expiresAt = DateTimeFormatter.ISO_INSTANT.format(generated.expiresAt().atZone(ZoneOffset.UTC));
        return new LoginResponse(generated.token(), user.getId(), user.getEmail(), expiresAt);
    }
}
