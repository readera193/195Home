package com.family195home.app.family.application;

import com.family195home.app.family.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    /** Demo 體驗帳號的 line_user_id 前綴；不是真實 LINE ID，LINE 推播須略過。 */
    public static final String DEMO_LINE_USER_ID_PREFIX = "demo-";

    /** Demo 體驗帳號的顯示名稱（所有 Demo 帳號相同，唯一性由 line_user_id 保證）。 */
    public static final String DEMO_DISPLAY_NAME = "使用者";

    private final UserRepository userRepository;
    private final LineLoginClient lineLoginClient;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;

    public UserService(
            UserRepository userRepository,
            LineLoginClient lineLoginClient,
            TokenIssuer tokenIssuer,
            Clock clock) {
        this.userRepository = userRepository;
        this.lineLoginClient = lineLoginClient;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
    }

    // FR-024：以 LINE 身分登入；首次登入自動建立帳號，之後沿用並同步暱稱（已自訂名稱者不覆蓋）
    @Transactional
    public LoginResult loginWithLine(String code) {
        LineProfile profile = lineLoginClient.fetchProfile(code);
        User user =
                userRepository
                        .findByLineUserId(profile.lineUserId())
                        .map(
                                existing -> {
                                    if (!existing.isDisplayNameCustomized()
                                            && !existing.getDisplayName()
                                                    .equals(profile.displayName())) {
                                        userRepository.updateDisplayName(
                                                existing.getId(), profile.displayName());
                                        existing.setDisplayName(profile.displayName());
                                    }
                                    return existing;
                                })
                        .orElseGet(
                                () ->
                                        userRepository.save(
                                                new User(
                                                        profile.lineUserId(),
                                                        profile.displayName(),
                                                        LocalDateTime.now(clock))));
        TokenIssuer.IssuedToken generated = tokenIssuer.issue(user.getId(), user.getDisplayName());
        return new LoginResult(
                generated.token(), user.getId(), user.getDisplayName(), generated.expiresAt());
    }

    // Demo 體驗：每次建立全新的隨機帳號（互不干擾），無需任何輸入
    @Transactional
    public LoginResult loginAsDemo() {
        String suffix = UUID.randomUUID().toString();
        User user =
                userRepository.save(
                        new User(
                                DEMO_LINE_USER_ID_PREFIX + suffix,
                                DEMO_DISPLAY_NAME,
                                LocalDateTime.now(clock)));
        TokenIssuer.IssuedToken generated = tokenIssuer.issue(user.getId(), user.getDisplayName());
        return new LoginResult(
                generated.token(), user.getId(), user.getDisplayName(), generated.expiresAt());
    }

    // Demo 體驗：建立不會登入的虛擬成員帳號，供 Demo 群組加入示範成員（line_user_id 帶 demo- 前綴，LINE 推播會略過）
    @Transactional
    public User createDemoMember(String displayName) {
        return userRepository.save(
                new User(
                        DEMO_LINE_USER_ID_PREFIX + UUID.randomUUID(),
                        displayName,
                        LocalDateTime.now(clock)));
    }

    public record LoginResult(String token, Long userId, String displayName, Instant expiresAt) {}
}
