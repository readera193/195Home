package com.family195home.app.family.application;

import com.family195home.app.shared.ApiException;
import com.family195home.app.shared.ErrorKind;
import com.family195home.app.family.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
    }

    // FR-024: Email 全系統唯一，重複註冊拒絕
    public User register(String email, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorKind.CONFLICT, "EMAIL_ALREADY_REGISTERED", "此 Email 已被註冊，請改用登入");
        }
        User user = new User(email, passwordEncoder.encode(rawPassword), LocalDateTime.now());
        userRepository.save(user);
        return user;
    }

    public LoginResult login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(ErrorKind.UNAUTHORIZED, "INVALID_CREDENTIALS", "帳號或密碼錯誤"));
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorKind.UNAUTHORIZED, "INVALID_CREDENTIALS", "帳號或密碼錯誤");
        }
        TokenIssuer.IssuedToken generated = tokenIssuer.issue(user.getId(), user.getEmail());
        return new LoginResult(generated.token(), user.getId(), user.getEmail(), generated.expiresAt());
    }

    public record LoginResult(String token, Long userId, String email, Instant expiresAt) {
    }
}
