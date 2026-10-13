package com.family195home.app.family.application;

import com.family195home.app.family.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository userRepository;
    private LineLoginClient lineLoginClient;
    private TokenIssuer tokenIssuer;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        lineLoginClient = mock(LineLoginClient.class);
        tokenIssuer = mock(TokenIssuer.class);
        userService = new UserService(userRepository, lineLoginClient, tokenIssuer);
    }

    @Test
    void loginWithLine_createsUserOnFirstLogin() {
        // FR-024：首次以 LINE 登入自動建立帳號
        when(lineLoginClient.fetchProfile("code")).thenReturn(new LineProfile("line-1", "小明"));
        when(userRepository.findByLineUserId("line-1")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(7L);
            return saved;
        });
        when(tokenIssuer.issue(7L, "小明")).thenReturn(new TokenIssuer.IssuedToken("jwt", Instant.now()));

        UserService.LoginResult result = userService.loginWithLine("code");

        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.displayName()).isEqualTo("小明");
        assertThat(result.token()).isEqualTo("jwt");
    }

    @Test
    void loginWithLine_reusesExistingUserAndSyncsDisplayName() {
        User existing = new User("line-1", "舊暱稱", LocalDateTime.now());
        existing.setId(7L);
        when(lineLoginClient.fetchProfile("code")).thenReturn(new LineProfile("line-1", "新暱稱"));
        when(userRepository.findByLineUserId("line-1")).thenReturn(Optional.of(existing));
        when(tokenIssuer.issue(7L, "新暱稱")).thenReturn(new TokenIssuer.IssuedToken("jwt", Instant.now()));

        UserService.LoginResult result = userService.loginWithLine("code");

        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.displayName()).isEqualTo("新暱稱");
        verify(userRepository).updateDisplayName(7L, "新暱稱");
        verify(userRepository, never()).save(any(User.class));
    }
}
