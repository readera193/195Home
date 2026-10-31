package com.family195home.app.expense.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.family195home.app.expense.domain.ExpenseRecord;
import com.family195home.app.expense.domain.PaymentAccount;
import com.family195home.app.expense.domain.PaymentAccountStatus;
import com.family195home.app.family.application.FamilyAccess;
import com.family195home.app.family.domain.FamilyMember;
import com.family195home.app.family.domain.MemberRole;
import com.family195home.app.family.domain.MemberStatus;
import com.family195home.app.shared.ApiException;
import com.family195home.app.shared.ErrorKind;
import com.family195home.app.shared.PagedResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExpenseServiceTest {

    private ExpenseRecordRepository expenseRecordRepository;
    private PaymentAccountRepository paymentAccountRepository;
    private FamilyAccess familyAccess;
    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        expenseRecordRepository = mock(ExpenseRecordRepository.class);
        paymentAccountRepository = mock(PaymentAccountRepository.class);
        familyAccess = mock(FamilyAccess.class);
        expenseService =
                new ExpenseService(
                        expenseRecordRepository,
                        paymentAccountRepository,
                        familyAccess,
                        5L,
                        Clock.systemDefaultZone());
        when(familyAccess.groupState(10L)).thenReturn(FamilyAccess.GroupState.ACTIVE);
        stubAccount(100L, 10L, PaymentAccountStatus.ACTIVE);
        stubAccount(200L, 10L, PaymentAccountStatus.ACTIVE);
    }

    @Test
    void create_rejectsAmountOutOfIntRange() {
        assertThatThrownBy(
                        () ->
                                expenseService.create(
                                        10L, 1L, 100L, new BigDecimal("3000000000"), "太大", null))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("AMOUNT_OUT_OF_RANGE"));
    }

    private void stubAccount(Long id, Long groupId, PaymentAccountStatus status) {
        PaymentAccount account = new PaymentAccount(1L, groupId, "帳戶", status, LocalDateTime.now());
        account.setId(id);
        when(paymentAccountRepository.findById(id)).thenReturn(Optional.of(account));
    }

    @Test
    void create_rejectsAccountFromAnotherGroup() {
        stubAccount(300L, 99L, PaymentAccountStatus.ACTIVE);

        assertThatThrownBy(() -> expenseService.create(10L, 1L, 300L, BigDecimal.TEN, "備註", null))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> {
                            assertThat(e.getCode()).isEqualTo("ACCOUNT_NOT_IN_GROUP");
                            assertThat(e.getKind()).isEqualTo(ErrorKind.FORBIDDEN);
                        });
        verify(expenseRecordRepository, never()).save(any());
    }

    @Test
    void create_rejectsDisabledAccount() {
        stubAccount(300L, 10L, PaymentAccountStatus.DISABLED);

        assertThatThrownBy(() -> expenseService.create(10L, 1L, 300L, BigDecimal.TEN, "備註", null))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ACCOUNT_DISABLED"));
    }

    @Test
    void create_rejectsUnknownAccount() {
        assertThatThrownBy(() -> expenseService.create(10L, 1L, 404L, BigDecimal.TEN, "備註", null))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void update_rejectsSwitchingToAccountFromAnotherGroup() {
        ExpenseRecord record = expenseRecord(1L, 10L, 100L);
        record.setPaymentAccountId(100L);
        record.setLockedByMemberId(2L);
        record.setLockedAt(LocalDateTime.now());
        when(expenseRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(familyAccess.requireMember(10L, 5L)).thenReturn(new FamilyAccess.MemberRef(2L));
        stubAccount(300L, 99L, PaymentAccountStatus.ACTIVE);

        assertThatThrownBy(
                        () -> expenseService.update(10L, 1L, 5L, 300L, BigDecimal.TEN, "備註", null))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ACCOUNT_NOT_IN_GROUP"));
        verify(expenseRecordRepository, never()).update(any());
    }

    @Test
    void update_allowsKeepingDisabledAccount() {
        ExpenseRecord record = expenseRecord(1L, 10L, 100L);
        record.setPaymentAccountId(300L);
        record.setLockedByMemberId(2L);
        record.setLockedAt(LocalDateTime.now());
        when(expenseRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(familyAccess.requireMember(10L, 5L)).thenReturn(new FamilyAccess.MemberRef(2L));
        stubAccount(300L, 10L, PaymentAccountStatus.DISABLED);

        ExpenseRecord result =
                expenseService.update(10L, 1L, 5L, 300L, BigDecimal.TEN, "改備註", null);

        assertThat(result.getNote()).isEqualTo("改備註");
    }

    @Test
    void lock_rejectsRecordFromAnotherGroup() {
        when(expenseRecordRepository.findById(1L))
                .thenReturn(Optional.of(expenseRecord(1L, 99L, 100L)));

        assertThatThrownBy(() -> expenseService.lock(10L, 1L, 2L))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("EXPENSE_NOT_IN_GROUP"));
        verify(expenseRecordRepository, never()).tryLock(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    void create_acceptsPositiveNegativeAndZeroIntegerAmounts() {
        // FR-016
        expenseService.create(10L, 1L, 100L, new BigDecimal("100"), "收入", null);
        expenseService.create(10L, 1L, 100L, new BigDecimal("-350"), "午餐", null);
        expenseService.create(10L, 1L, 100L, BigDecimal.ZERO, "僅記錄", null);

        verify(expenseRecordRepository, times(3)).save(any());
    }

    @Test
    void create_rejectsDecimalAmount() {
        // FR-016
        assertThatThrownBy(
                        () ->
                                expenseService.create(
                                        10L, 1L, 100L, new BigDecimal("100.5"), "午餐", null))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("AMOUNT_MUST_BE_INTEGER"));
        verify(expenseRecordRepository, never()).save(any());
    }

    @Test
    void create_rejectsBlankNote() {
        assertThatThrownBy(() -> expenseService.create(10L, 1L, 100L, BigDecimal.TEN, " ", null))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("NOTE_REQUIRED"));
    }

    @Test
    void create_rejectsWhenGroupDissolved() {
        // FR-018
        when(familyAccess.groupState(10L)).thenReturn(FamilyAccess.GroupState.DISSOLVED);

        assertThatThrownBy(() -> expenseService.create(10L, 1L, 100L, BigDecimal.TEN, "備註", null))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("GROUP_DISSOLVED"));
    }

    @Test
    void create_defaultsOccurredAtToNowWhenNotSpecified() {
        // FR-005
        LocalDateTime before = LocalDateTime.now();
        ExpenseRecord record = expenseService.create(10L, 1L, 100L, BigDecimal.ONE, "備註", null);
        assertThat(record.getOccurredAt()).isAfterOrEqualTo(before);
    }

    @Test
    void lock_succeedsWhenNotCurrentlyLocked() {
        // FR-027
        when(expenseRecordRepository.tryLock(eq(1L), eq(2L), any(), anyLong())).thenReturn(1);
        ExpenseRecord locked = new ExpenseRecord();
        locked.setId(1L);
        locked.setFamilyGroupId(10L);
        locked.setLockedByMemberId(2L);
        locked.setLockedAt(LocalDateTime.now());
        when(expenseRecordRepository.findById(1L)).thenReturn(Optional.of(locked));

        ExpenseRecord result = expenseService.lock(10L, 1L, 2L);

        assertThat(result.getLockedByMemberId()).isEqualTo(2L);
    }

    @Test
    void lock_rejectsWhenAlreadyLockedByOther() {
        // FR-027
        when(expenseRecordRepository.findById(1L))
                .thenReturn(Optional.of(expenseRecord(1L, 10L, 100L)));
        when(expenseRecordRepository.tryLock(eq(1L), eq(2L), any(), anyLong())).thenReturn(0);

        assertThatThrownBy(() -> expenseService.lock(10L, 1L, 2L))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("RECORD_LOCKED"));
    }

    @Test
    void update_rejectsWhenCallerNotInGroup() {
        // FR-017、FR-019：非群組在職成員不可操作
        ExpenseRecord record = expenseRecord(1L, 10L, 100L);
        when(expenseRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(familyAccess.requireMember(10L, 5L))
                .thenThrow(new ApiException(ErrorKind.FORBIDDEN, "NOT_GROUP_MEMBER", "您不是該家庭群組成員"));

        assertThatThrownBy(
                        () -> expenseService.update(10L, 1L, 5L, 100L, BigDecimal.TEN, "備註", null))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("NOT_GROUP_MEMBER"));
    }

    @Test
    void update_rejectsRecordFromAnotherGroup() {
        // FR-017：紀錄屬於其他群組時回傳 403
        ExpenseRecord record = expenseRecord(1L, 99L, 100L);
        when(expenseRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(familyAccess.requireMember(10L, 5L))
                .thenReturn(new FamilyAccess.MemberRef(member(2L, 5L, MemberRole.MEMBER).getId()));

        assertThatThrownBy(
                        () -> expenseService.update(10L, 1L, 5L, 100L, BigDecimal.TEN, "備註", null))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> {
                            assertThat(e.getCode()).isEqualTo("EXPENSE_NOT_IN_GROUP");
                            assertThat(e.getKind()).isEqualTo(ErrorKind.FORBIDDEN);
                        });
    }

    @Test
    void update_allowsRegularMemberEvenIfNotAuthor() {
        // FR-019：一般成員可編輯其他成員新增的紀錄
        ExpenseRecord record = expenseRecord(1L, 10L, 999L);
        record.setLockedByMemberId(2L);
        record.setLockedAt(LocalDateTime.now());
        when(expenseRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        FamilyMember regular = member(2L, 5L, MemberRole.MEMBER);
        when(familyAccess.requireMember(10L, 5L))
                .thenReturn(new FamilyAccess.MemberRef(regular.getId()));

        ExpenseRecord result =
                expenseService.update(10L, 1L, 5L, 200L, new BigDecimal("30"), "他人紀錄", null);

        assertThat(result.getAmount()).isEqualTo(30);
    }

    @Test
    void delete_allowsRegularMemberEvenIfNotAuthor() {
        // FR-019
        ExpenseRecord record = expenseRecord(1L, 10L, 999L);
        record.setLockedByMemberId(2L);
        record.setLockedAt(LocalDateTime.now());
        when(expenseRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        FamilyMember regular = member(2L, 5L, MemberRole.MEMBER);
        when(familyAccess.requireMember(10L, 5L))
                .thenReturn(new FamilyAccess.MemberRef(regular.getId()));

        expenseService.delete(10L, 1L, 5L);

        verify(expenseRecordRepository).delete(1L);
    }

    @Test
    void update_rejectsWhenLockNotHeldByCaller() {
        // FR-027
        ExpenseRecord record = expenseRecord(1L, 10L, 100L);
        record.setLockedByMemberId(999L);
        record.setLockedAt(LocalDateTime.now());
        when(expenseRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        FamilyMember author = member(100L, record.getAuthorMemberId(), MemberRole.MEMBER);
        when(familyAccess.requireMember(10L, 5L))
                .thenReturn(new FamilyAccess.MemberRef(author.getId()));

        assertThatThrownBy(
                        () -> expenseService.update(10L, 1L, 5L, 100L, BigDecimal.TEN, "備註", null))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LOCK_NOT_HELD_BY_CALLER"));
    }

    @Test
    void update_allowsAdminEvenIfNotAuthor() {
        // FR-019：管理者可編輯任何成員新增的支出紀錄
        ExpenseRecord record = expenseRecord(1L, 10L, 999L);
        record.setLockedByMemberId(1L);
        record.setLockedAt(LocalDateTime.now());
        when(expenseRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        FamilyMember admin = member(1L, 5L, MemberRole.ADMIN);
        when(familyAccess.requireMember(10L, 5L))
                .thenReturn(new FamilyAccess.MemberRef(admin.getId()));

        ExpenseRecord result =
                expenseService.update(10L, 1L, 5L, 200L, new BigDecimal("-500"), "更新後備註", null);

        assertThat(result.getAmount()).isEqualTo(-500);
        verify(expenseRecordRepository).releaseLock(1L);
    }

    private FamilyMember member(Long memberId, Long userId, MemberRole role) {
        FamilyMember member =
                new FamilyMember(10L, userId, MemberStatus.ACTIVE, role, LocalDateTime.now());
        member.setId(memberId);
        return member;
    }

    private ExpenseRecord expenseRecord(Long id, Long familyGroupId, Long authorMemberId) {
        ExpenseRecord record = new ExpenseRecord();
        record.setId(id);
        record.setFamilyGroupId(familyGroupId);
        record.setAuthorMemberId(authorMemberId);
        record.setAmount(100);
        record.setNote("原始備註");
        record.setOccurredAt(LocalDateTime.now());
        return record;
    }

    @Test
    void listPage_passesOffsetAndComputesTotalPages() {
        // FR-007、SC-003：第 2 頁（page=1）、每頁 20 筆 → offset 20
        ExpenseRecord record = new ExpenseRecord();
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        when(expenseRecordRepository.countByFilter(10L, null, null, start, end)).thenReturn(45L);
        when(expenseRecordRepository.findPageByFilter(10L, null, null, start, end, 20, 20))
                .thenReturn(List.of(record));

        PagedResult<ExpenseRecord> result =
                expenseService.listPage(10L, null, null, start, end, 1, 20);

        assertThat(result.items()).containsExactly(record);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(45L);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void listPage_rejectsInvalidPageParams() {
        assertThatThrownBy(() -> expenseService.listPage(10L, null, null, null, null, -1, 20))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> {
                            assertThat(e.getKind()).isEqualTo(ErrorKind.BAD_REQUEST);
                            assertThat(e.getCode()).isEqualTo("INVALID_PAGE_PARAMS");
                        });
        assertThatThrownBy(() -> expenseService.listPage(10L, null, null, null, null, 0, 0))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(
                        () ->
                                expenseService.listPage(
                                        10L,
                                        null,
                                        null,
                                        null,
                                        null,
                                        0,
                                        ExpenseService.MAX_PAGE_SIZE + 1))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(ignoreStubs(expenseRecordRepository));
    }

    @Test
    void listPage_rejectsStartDateAfterEndDate() {
        assertThatThrownBy(
                        () ->
                                expenseService.listPage(
                                        10L,
                                        null,
                                        null,
                                        LocalDate.of(2026, 9, 30),
                                        LocalDate.of(2026, 9, 1),
                                        0,
                                        20))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVALID_DATE_RANGE"));
    }

    private ExpenseRecord lockedRecord(Long lockedByMemberId, LocalDateTime lockedAt) {
        ExpenseRecord record = new ExpenseRecord();
        record.setId(500L);
        record.setFamilyGroupId(10L);
        record.setLockedByMemberId(lockedByMemberId);
        record.setLockedAt(lockedAt);
        when(expenseRecordRepository.findById(500L)).thenReturn(Optional.of(record));
        return record;
    }

    @Test
    void unlock_releasesLockHeldByCaller() {
        lockedRecord(1L, LocalDateTime.now());
        when(familyAccess.requireMember(10L, 7L)).thenReturn(new FamilyAccess.MemberRef(1L));

        expenseService.unlock(500L, 7L);

        verify(expenseRecordRepository).releaseLock(500L);
    }

    @Test
    void unlock_rejectsWhenLockHeldByAnotherMember() {
        // FR-027：他人仍持有有效鎖時，不可被其他成員解除
        lockedRecord(2L, LocalDateTime.now());
        when(familyAccess.requireMember(10L, 7L)).thenReturn(new FamilyAccess.MemberRef(1L));

        assertThatThrownBy(() -> expenseService.unlock(500L, 7L))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> {
                            assertThat(e.getKind()).isEqualTo(ErrorKind.FORBIDDEN);
                            assertThat(e.getCode()).isEqualTo("LOCK_NOT_HELD_BY_CALLER");
                        });
        verify(expenseRecordRepository, never()).releaseLock(any());
    }

    @Test
    void unlock_allowsClearingAnotherMembersExpiredLock() {
        lockedRecord(2L, LocalDateTime.now().minusMinutes(30));
        when(familyAccess.requireMember(10L, 7L)).thenReturn(new FamilyAccess.MemberRef(1L));

        expenseService.unlock(500L, 7L);

        verify(expenseRecordRepository).releaseLock(500L);
    }

    @Test
    void unlock_isNoOpWhenNotLocked() {
        lockedRecord(null, null);
        when(familyAccess.requireMember(10L, 7L)).thenReturn(new FamilyAccess.MemberRef(1L));

        expenseService.unlock(500L, 7L);

        verify(expenseRecordRepository, never()).releaseLock(any());
    }

    @Test
    void unlock_rejectsNonMemberOfTheRecordsGroup() {
        // FR-017：非該群組在職成員不可對紀錄做任何操作
        lockedRecord(1L, LocalDateTime.now());
        when(familyAccess.requireMember(10L, 99L))
                .thenThrow(new ApiException(ErrorKind.FORBIDDEN, "NOT_GROUP_MEMBER", "不是群組成員"));

        assertThatThrownBy(() -> expenseService.unlock(500L, 99L))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("NOT_GROUP_MEMBER"));
        verify(expenseRecordRepository, never()).releaseLock(any());
    }

    @Test
    void unlock_unknownExpenseIsNotFound() {
        when(expenseRecordRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.unlock(404L, 7L))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("EXPENSE_NOT_FOUND"));
    }
}
