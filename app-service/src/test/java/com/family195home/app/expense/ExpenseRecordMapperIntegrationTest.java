package com.family195home.app.expense;

import com.family195home.app.expense.domain.ExpenseRecord;
import com.family195home.app.expense.domain.PaymentAccount;
import com.family195home.app.expense.domain.PaymentAccountStatus;
import com.family195home.app.expense.infrastructure.persistence.ExpenseRecordMapper;
import com.family195home.app.expense.infrastructure.persistence.PaymentAccountMapper;
import com.family195home.app.family.domain.FamilyGroup;
import com.family195home.app.family.domain.FamilyGroupStatus;
import com.family195home.app.family.infrastructure.persistence.FamilyGroupMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 驗證 Flyway migration 可於真實 MySQL 執行，且 ExpenseRecordMapper 的自訂 SQL
 * （篩選查詢、併發鎖定條件式 UPDATE）與 MySQL 方言相容（見 tasks.md T071/T072）。
 */
@Testcontainers
@SpringBootTest
class ExpenseRecordMapperIntegrationTest {

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
    private FamilyGroupMapper familyGroupMapper;

    @Autowired
    private PaymentAccountMapper paymentAccountMapper;

    @Autowired
    private ExpenseRecordMapper expenseRecordMapper;

    @Test
    void filterQuery_andConditionalLockUpdate_workAgainstRealMySql() {
        FamilyGroup group = new FamilyGroup("整合測試家庭", FamilyGroupStatus.ACTIVE, "ITCODE01", LocalDateTime.now());
        familyGroupMapper.insert(group);

        PaymentAccount account = new PaymentAccount(1L, group.getId(), "現金", PaymentAccountStatus.ACTIVE, LocalDateTime.now());
        paymentAccountMapper.insert(account);

        LocalDateTime now = LocalDateTime.now();
        ExpenseRecord record = new ExpenseRecord();
        record.setFamilyGroupId(group.getId());
        record.setPaymentAccountId(account.getId());
        record.setAuthorMemberId(1L);
        record.setAmount(-350);
        record.setNote("整合測試支出");
        record.setOccurredAt(now);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        expenseRecordMapper.insert(record);

        // 篩選查詢：依 familyGroupId + paymentAccountId + 月份區間
        List<ExpenseRecord> found = expenseRecordMapper.selectByFilter(
                group.getId(), account.getId(), null, now.withDayOfMonth(1).toLocalDate().atStartOfDay(), now.plusMonths(1));
        assertThat(found).extracting(ExpenseRecord::getId).contains(record.getId());

        // 併發鎖定：第一次取得鎖成功
        int firstLock = expenseRecordMapper.tryLock(record.getId(), 1L, now, now.minusMinutes(5));
        assertThat(firstLock).isEqualTo(1);

        // 第二個人在鎖定未逾時前嘗試取得鎖應失敗（影響列數為 0）
        int secondLock = expenseRecordMapper.tryLock(record.getId(), 2L, now, now.minusMinutes(5));
        assertThat(secondLock).isEqualTo(0);

        expenseRecordMapper.releaseLock(record.getId());
        ExpenseRecord afterRelease = expenseRecordMapper.selectById(record.getId());
        assertThat(afterRelease.getLockedByMemberId()).isNull();
    }
}
