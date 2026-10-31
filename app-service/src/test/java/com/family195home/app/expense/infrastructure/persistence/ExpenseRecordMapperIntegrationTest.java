package com.family195home.app.expense.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.family195home.app.expense.domain.ExpenseRecord;
import com.family195home.app.expense.domain.PaymentAccount;
import com.family195home.app.expense.domain.PaymentAccountStatus;
import com.family195home.app.family.domain.FamilyGroup;
import com.family195home.app.family.domain.FamilyGroupStatus;
import com.family195home.app.family.infrastructure.persistence.FamilyGroupMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 驗證 Flyway migration 可於真實 MySQL 執行，且 ExpenseRecordMapper 的自訂 SQL （篩選查詢、併發鎖定條件式 UPDATE）與 MySQL
 * 方言相容（見 tasks.md T071/T072）。
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class ExpenseRecordMapperIntegrationTest {

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

    @Autowired private FamilyGroupMapper familyGroupMapper;

    @Autowired private PaymentAccountMapper paymentAccountMapper;

    @Autowired private ExpenseRecordMapper expenseRecordMapper;

    @Test
    void filterQuery_andConditionalLockUpdate_workAgainstRealMySql() {
        FamilyGroup group =
                new FamilyGroup(
                        "整合測試家庭", FamilyGroupStatus.ACTIVE, "ITCODE01", LocalDateTime.now());
        familyGroupMapper.insert(group);

        PaymentAccount account =
                new PaymentAccount(
                        1L, group.getId(), "現金", PaymentAccountStatus.ACTIVE, LocalDateTime.now());
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
        List<ExpenseRecord> found =
                expenseRecordMapper.selectByFilter(
                        group.getId(),
                        account.getId(),
                        null,
                        now.withDayOfMonth(1).toLocalDate().atStartOfDay(),
                        now.plusMonths(1));
        assertThat(found).extracting(ExpenseRecord::getId).contains(record.getId());

        // 已被使用的支付帳戶 id（FR-022）：有支出的帳戶出現，未使用的不出現
        PaymentAccount unused =
                new PaymentAccount(
                        1L, group.getId(), "未使用", PaymentAccountStatus.ACTIVE, LocalDateTime.now());
        paymentAccountMapper.insert(unused);
        assertThat(expenseRecordMapper.selectUsedPaymentAccountIds(group.getId()))
                .contains(account.getId())
                .doesNotContain(unused.getId());

        // 併發鎖定：第一次取得鎖成功
        int firstLock = expenseRecordMapper.tryLock(record.getId(), 1L, now, now.minusMinutes(5));
        assertThat(firstLock).isEqualTo(1);

        // 第二個人在鎖定未逾時前嘗試取得鎖應失敗（影響列數為 0）
        int secondLock = expenseRecordMapper.tryLock(record.getId(), 2L, now, now.minusMinutes(5));
        assertThat(secondLock).isEqualTo(0);

        assertThat(
                        expenseRecordMapper.releaseLockIfHeldOrExpired(
                                record.getId(), 1L, now.minusMinutes(5)))
                .isEqualTo(1);
        ExpenseRecord afterRelease = expenseRecordMapper.selectById(record.getId());
        assertThat(afterRelease.getLockedByMemberId()).isNull();
    }

    @Test
    void conditionalRelease_neverClearsAnotherMembersValidLock() {
        FamilyGroup group =
                new FamilyGroup(
                        "條件釋放測試家庭", FamilyGroupStatus.ACTIVE, "ITCODE04", LocalDateTime.now());
        familyGroupMapper.insert(group);
        PaymentAccount account =
                new PaymentAccount(
                        1L, group.getId(), "現金", PaymentAccountStatus.ACTIVE, LocalDateTime.now());
        paymentAccountMapper.insert(account);
        LocalDateTime now = LocalDateTime.now();
        ExpenseRecord record = new ExpenseRecord();
        record.setFamilyGroupId(group.getId());
        record.setPaymentAccountId(account.getId());
        record.setAuthorMemberId(1L);
        record.setAmount(100);
        record.setNote("釋放鎖測試");
        record.setOccurredAt(now);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        expenseRecordMapper.insert(record);

        // 成員 1 的鎖已逾時，成員 2 重新取得鎖
        LocalDateTime staleLockAt = now.minusMinutes(30);
        expenseRecordMapper.tryLock(record.getId(), 1L, staleLockAt, staleLockAt.minusMinutes(5));
        assertThat(expenseRecordMapper.tryLock(record.getId(), 2L, now, now.minusMinutes(5)))
                .isEqualTo(1);

        // 原持有者成員 1 以舊認知釋放：影響 0 列，成員 2 的鎖仍在
        assertThat(
                        expenseRecordMapper.releaseLockIfHeldOrExpired(
                                record.getId(), 1L, now.minusMinutes(5)))
                .isEqualTo(0);
        assertThat(expenseRecordMapper.selectById(record.getId()).getLockedByMemberId())
                .isEqualTo(2L);

        // 成員 2 的鎖逾時後，其他成員即可清除
        LocalDateTime later = now.plusMinutes(10);
        assertThat(
                        expenseRecordMapper.releaseLockIfHeldOrExpired(
                                record.getId(), 1L, later.minusMinutes(5)))
                .isEqualTo(1);
        assertThat(expenseRecordMapper.selectById(record.getId()).getLockedByMemberId()).isNull();
    }

    @Test
    void conditionalWrite_onlySucceedsForTheCurrentLockHolder() {
        FamilyGroup group =
                new FamilyGroup(
                        "條件寫入測試家庭", FamilyGroupStatus.ACTIVE, "ITCODE03", LocalDateTime.now());
        familyGroupMapper.insert(group);
        PaymentAccount account =
                new PaymentAccount(
                        1L, group.getId(), "現金", PaymentAccountStatus.ACTIVE, LocalDateTime.now());
        paymentAccountMapper.insert(account);
        LocalDateTime now = LocalDateTime.now();
        ExpenseRecord record = new ExpenseRecord();
        record.setFamilyGroupId(group.getId());
        record.setPaymentAccountId(account.getId());
        record.setAuthorMemberId(1L);
        record.setAmount(100);
        record.setNote("原本");
        record.setOccurredAt(now);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        expenseRecordMapper.insert(record);
        expenseRecordMapper.tryLock(record.getId(), 1L, now, now.minusMinutes(5));

        // 鎖屬於成員 1：成員 2 的寫入與刪除都落空，內容不變、鎖仍在
        record.setNote("被覆寫");
        assertThat(expenseRecordMapper.updateIfLockHeld(record, 2L)).isEqualTo(0);
        assertThat(expenseRecordMapper.deleteByIdIfLockHeld(record.getId(), 2L)).isEqualTo(0);
        ExpenseRecord untouched = expenseRecordMapper.selectById(record.getId());
        assertThat(untouched.getNote()).isEqualTo("原本");
        assertThat(untouched.getLockedByMemberId()).isEqualTo(1L);

        // 持鎖者成員 1：更新成功，並在同一個 UPDATE 釋放鎖
        assertThat(expenseRecordMapper.updateIfLockHeld(record, 1L)).isEqualTo(1);
        ExpenseRecord updated = expenseRecordMapper.selectById(record.getId());
        assertThat(updated.getNote()).isEqualTo("被覆寫");
        assertThat(updated.getLockedByMemberId()).isNull();

        // 鎖已釋放後，再以舊鎖持有者身分刪除也要落空；重新取得鎖後才能刪除
        assertThat(expenseRecordMapper.deleteByIdIfLockHeld(record.getId(), 1L)).isEqualTo(0);
        expenseRecordMapper.tryLock(record.getId(), 1L, now, now.minusMinutes(5));
        assertThat(expenseRecordMapper.deleteByIdIfLockHeld(record.getId(), 1L)).isEqualTo(1);
        assertThat(expenseRecordMapper.selectById(record.getId())).isNull();
    }

    @Test
    void pageQuery_ordersNewestFirstWithStableTieBreakAndCountsAll() {
        FamilyGroup group =
                new FamilyGroup(
                        "分頁測試家庭", FamilyGroupStatus.ACTIVE, "ITCODE02", LocalDateTime.now());
        familyGroupMapper.insert(group);
        PaymentAccount account =
                new PaymentAccount(
                        1L, group.getId(), "現金", PaymentAccountStatus.ACTIVE, LocalDateTime.now());
        paymentAccountMapper.insert(account);

        // 5 筆，其中兩筆 occurred_at 完全相同，用來驗證 id 次要排序使翻頁不重複、不遺漏
        LocalDateTime base = LocalDateTime.of(2026, 9, 10, 12, 0);
        for (int i = 0; i < 5; i++) {
            ExpenseRecord record = new ExpenseRecord();
            record.setFamilyGroupId(group.getId());
            record.setPaymentAccountId(account.getId());
            record.setAuthorMemberId(1L);
            record.setAmount(i);
            record.setNote("分頁 " + i);
            record.setOccurredAt(i >= 3 ? base : base.minusDays(i));
            record.setCreatedAt(base);
            record.setUpdatedAt(base);
            expenseRecordMapper.insert(record);
        }

        LocalDateTime monthStart = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime monthEnd = LocalDateTime.of(2026, 10, 1, 0, 0);

        long total =
                expenseRecordMapper.countByFilter(group.getId(), null, null, monthStart, monthEnd);
        assertThat(total).isEqualTo(5);

        List<ExpenseRecord> page0 =
                expenseRecordMapper.selectPageByFilter(
                        group.getId(), null, null, monthStart, monthEnd, 0, 2);
        List<ExpenseRecord> page1 =
                expenseRecordMapper.selectPageByFilter(
                        group.getId(), null, null, monthStart, monthEnd, 2, 2);
        List<ExpenseRecord> page2 =
                expenseRecordMapper.selectPageByFilter(
                        group.getId(), null, null, monthStart, monthEnd, 4, 2);

        assertThat(page0).hasSize(2);
        assertThat(page1).hasSize(2);
        assertThat(page2).hasSize(1);
        assertThat(
                        java.util.stream.Stream.of(page0, page1, page2)
                                .flatMap(List::stream)
                                .map(ExpenseRecord::getId))
                .doesNotHaveDuplicates()
                .hasSize(5);
        // 時間相同的兩筆以 id 由大到小排在最前面
        assertThat(page0.get(0).getId()).isGreaterThan(page0.get(1).getId());
    }
}
