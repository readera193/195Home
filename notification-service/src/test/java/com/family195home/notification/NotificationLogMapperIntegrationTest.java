package com.family195home.notification;

import com.family195home.notification.domain.NotificationLog;
import com.family195home.notification.domain.NotificationStatus;
import com.family195home.notification.infrastructure.persistence.NotificationLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 驗證 Flyway migration 可於真實 MySQL 執行，且 NotificationLogMapper 的自訂 SQL
 * 與 MySQL 方言相容（見 tasks.md T071/T072）。
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(properties = {
        // line-bot-spring-boot 的自動組態需要非空白的 channel token/secret 才能建立 Bean，
        // 本測試僅驗證資料庫存取層，故帶入測試用假值即可，不會真的呼叫 LINE API
        "line.bot.channel-token=integration-test-token",
        "line.bot.channel-secret=integration-test-secret"
})
class NotificationLogMapperIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("notificationdb")
            .withUsername("notification")
            .withPassword("notification");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private NotificationLogMapper notificationLogMapper;

    @Test
    void insertAndSelectByGroupAndMonth_workAgainstRealMySql() {
        NotificationLog log = new NotificationLog(10L, 1L, "line-user-1", "2026-09");
        log.setStatus(NotificationStatus.SUCCESS);
        log.setAttempts(1);
        log.setLastAttemptAt(LocalDateTime.now());
        notificationLogMapper.insert(log);

        List<NotificationLog> found = notificationLogMapper.selectByGroupAndMonth(10L, "2026-09");

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getStatus()).isEqualTo(NotificationStatus.SUCCESS);
        assertThat(found.get(0).getLineUserId()).isEqualTo("line-user-1");
    }
}
