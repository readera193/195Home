package com.family195home.app.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 驗證過期 Demo 資料清理：只刪過期且全為 Demo 帳號的群組，不影響新資料與有真實使用者的群組。 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class DemoDataCleanupIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("appdb")
                    .withUsername("app")
                    .withPassword("app");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired private DemoDataCleanupService cleanupService;

    @Autowired private JdbcTemplate jdbc;

    @Test
    void cleanup_removesOnlyExpiredAllDemoGroupsAndOrphanDemoUsers() {
        LocalDateTime old = LocalDateTime.now().minusDays(3);
        LocalDateTime fresh = LocalDateTime.now();

        long expiredDemoGroup =
                group("過期 Demo 群組", old, user("demo-old-1", old), user("demo-old-2", old));
        long freshDemoGroup = group("新的 Demo 群組", fresh, user("demo-new-1", fresh));
        long realUserGroup =
                group("有真實使用者的群組", old, user("demo-old-3", old), user("U-real-line-id", old));
        long orphanDemoUser = user("demo-orphan", old);
        long freshOrphanDemoUser = user("demo-orphan-fresh", fresh);

        int deleted = cleanupService.cleanup(LocalDateTime.now().minusHours(24));

        assertThat(deleted).isEqualTo(1);
        assertThat(exists("family_groups", expiredDemoGroup)).isFalse();
        assertThat(count("family_members", "family_group_id", expiredDemoGroup)).isZero();
        assertThat(count("payment_accounts", "family_group_id", expiredDemoGroup)).isZero();
        assertThat(count("expense_records", "family_group_id", expiredDemoGroup)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM users WHERE line_user_id IN ('demo-old-1','demo-old-2')",
                                Long.class))
                .isZero();

        assertThat(exists("family_groups", freshDemoGroup)).isTrue();
        assertThat(count("expense_records", "family_group_id", freshDemoGroup)).isEqualTo(1);
        assertThat(exists("family_groups", realUserGroup)).isTrue();
        assertThat(exists("users", orphanDemoUser)).isFalse();
        assertThat(exists("users", freshOrphanDemoUser)).isTrue();
    }

    private long user(String lineUserId, LocalDateTime createdAt) {
        jdbc.update(
                "INSERT INTO users (line_user_id, display_name, created_at) VALUES (?, 'u', ?)",
                lineUserId,
                createdAt);
        return jdbc.queryForObject(
                "SELECT id FROM users WHERE line_user_id = ?", Long.class, lineUserId);
    }

    /** 建立群組、各成員、一個支付帳戶與一筆支出。 */
    private long group(String name, LocalDateTime createdAt, long... userIds) {
        jdbc.update(
                "INSERT INTO family_groups (name, status, invite_code, created_at) VALUES (?, 'ACTIVE', ?, ?)",
                name,
                name.hashCode() + "",
                createdAt);
        long groupId =
                jdbc.queryForObject(
                        "SELECT id FROM family_groups WHERE name = ?", Long.class, name);
        long firstMember = 0;
        for (long userId : userIds) {
            jdbc.update(
                    "INSERT INTO family_members (family_group_id, user_id, status, role, joined_at) "
                            + "VALUES (?, ?, 'ACTIVE', 'MEMBER', ?)",
                    groupId,
                    userId,
                    createdAt);
            if (firstMember == 0) {
                firstMember =
                        jdbc.queryForObject(
                                "SELECT id FROM family_members WHERE family_group_id = ? AND user_id = ?",
                                Long.class,
                                groupId,
                                userId);
            }
        }
        jdbc.update(
                "INSERT INTO payment_accounts (family_member_id, family_group_id, name, status, created_at) "
                        + "VALUES (?, ?, '現金', 'ACTIVE', ?)",
                firstMember,
                groupId,
                createdAt);
        long accountId =
                jdbc.queryForObject(
                        "SELECT id FROM payment_accounts WHERE family_group_id = ?",
                        Long.class,
                        groupId);
        jdbc.update(
                "INSERT INTO expense_records (family_group_id, payment_account_id, author_member_id, amount, note, "
                        + "occurred_at, created_at, updated_at) VALUES (?, ?, ?, -100, '早餐', ?, ?, ?)",
                groupId,
                accountId,
                firstMember,
                createdAt,
                createdAt,
                createdAt);
        return groupId;
    }

    private boolean exists(String table, long id) {
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM " + table + " WHERE id = ?", Long.class, id)
                > 0;
    }

    private long count(String table, String column, long value) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Long.class, value);
    }
}
