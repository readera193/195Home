package com.family195home.app.family.application;

import com.family195home.app.family.domain.User;
import com.family195home.app.shared.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private TokenIssuer tokenIssuer;
    private UserService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tokenIssuer = mock(TokenIssuer.class);
        service = new UserService(userRepository, passwordEncoder, tokenIssuer);
    }

    @Test
    void register_rejectsDuplicateEmail() {
        // FR-024：Email 全系統唯一
        when(userRepository.existsByEmail("a@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("a@example.com", "pw"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("EMAIL_ALREADY_REGISTERED"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_withValidCredentials_returnsTokenFromIssuer() {
        User user = new User("a@example.com", "hash", LocalDateTime.now());
        user.setId(7L);
        Instant expiry = Instant.now().plusSeconds(7200);
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(tokenIssuer.issue(7L, "a@example.com")).thenReturn(new TokenIssuer.IssuedToken("jwt", expiry));

        UserService.LoginResult result = service.login("a@example.com", "pw");

        assertThat(result.token()).isEqualTo("jwt");
        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.expiresAt()).isEqualTo(expiry);
    }

    @Test
    void login_withWrongPassword_doesNotIssueToken() {
        User user = new User("a@example.com", "hash", LocalDateTime.now());
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login("a@example.com", "bad"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVALID_CREDENTIALS"));
        verifyNoInteractions(tokenIssuer);
    }
}
