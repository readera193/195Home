package com.family195home.app.expense.infrastructure.persistence;

import com.family195home.app.expense.domain.PaymentAccount;
import com.family195home.app.expense.domain.PaymentAccountStatus;
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

import static org.assertj.core.api.Assertions.assertThat;

/** 驗證 PaymentAccountMapper 依群組與狀態篩選的 SQL（見 tasks.md T071/T096）。 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class PaymentAccountMapperIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("appdb")
            .withUsername("app")
            .withPassword("app");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private PaymentAccountMapper paymentAccountMapper;

    @Test
    void selectByGroupId_filtersByGroupAndOptionalStatus() {
        long groupId = 9001L;
        insert(groupId, "現金", PaymentAccountStatus.ACTIVE);
        insert(groupId, "舊信用卡", PaymentAccountStatus.DISABLED);
        insert(9002L, "別人的帳戶", PaymentAccountStatus.ACTIVE);

        assertThat(paymentAccountMapper.selectByGroupId(groupId, null))
                .extracting(PaymentAccount::getName).containsExactlyInAnyOrder("現金", "舊信用卡");
        assertThat(paymentAccountMapper.selectByGroupId(groupId, "ACTIVE"))
                .extracting(PaymentAccount::getName).containsExactly("現金");
        assertThat(paymentAccountMapper.selectByGroupId(groupId, "DISABLED"))
                .extracting(PaymentAccount::getName).containsExactly("舊信用卡");
    }

    private void insert(long groupId, String name, PaymentAccountStatus status) {
        paymentAccountMapper.insert(new PaymentAccount(1L, groupId, name, status, LocalDateTime.now()));
    }
}
