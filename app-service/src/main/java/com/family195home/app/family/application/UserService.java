package com.family195home.app.family.application;

import com.family195home.app.family.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final LineLoginClient lineLoginClient;
    private final TokenIssuer tokenIssuer;

    public UserService(UserRepository userRepository, LineLoginClient lineLoginClient, TokenIssuer tokenIssuer) {
        this.userRepository = userRepository;
        this.lineLoginClient = lineLoginClient;
        this.tokenIssuer = tokenIssuer;
    }

    // FR-024：以 LINE 身分登入；首次登入自動建立帳號，之後沿用並同步暱稱
    @Transactional
    public LoginResult loginWithLine(String code) {
        LineProfile profile = lineLoginClient.fetchProfile(code);
        User user = userRepository.findByLineUserId(profile.lineUserId())
                .map(existing -> {
                    if (!existing.getDisplayName().equals(profile.displayName())) {
                        userRepository.updateDisplayName(existing.getId(), profile.displayName());
                        existing.setDisplayName(profile.displayName());
                    }
                    return existing;
                })
                .orElseGet(() -> userRepository.save(
                        new User(profile.lineUserId(), profile.displayName(), LocalDateTime.now())));
        TokenIssuer.IssuedToken generated = tokenIssuer.issue(user.getId(), user.getDisplayName());
        return new LoginResult(generated.token(), user.getId(), user.getDisplayName(), generated.expiresAt());
    }

    public record LoginResult(String token, Long userId, String displayName, Instant expiresAt) {
    }
}
