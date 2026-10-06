package com.family195home.app.expense;

import com.family195home.app.common.ApiException;
import com.family195home.app.expense.application.ExpenseRecordRepository;
import com.family195home.app.expense.domain.ExpenseRecord;
import com.family195home.app.expense.service.ExpenseService;
import com.family195home.app.family.domain.FamilyGroup;
import com.family195home.app.family.domain.FamilyGroupStatus;
import com.family195home.app.family.domain.FamilyMember;
import com.family195home.app.family.domain.MemberRole;
import com.family195home.app.family.domain.MemberStatus;
import com.family195home.app.family.service.FamilyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExpenseServiceTest {

    private ExpenseRecordRepository expenseRecordRepository;
    private FamilyService familyService;
    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        expenseRecordRepository = mock(ExpenseRecordRepository.class);
        familyService = mock(FamilyService.class);
        expenseService = new ExpenseService(expenseRecordRepository, familyService, 5L);
        when(familyService.getGroup(10L)).thenReturn(Optional.of(activeGroup()));
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
        assertThatThrownBy(() -> expenseService.create(10L, 1L, 100L, new BigDecimal("100.5"), "午餐", null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("AMOUNT_MUST_BE_INTEGER"));
        verify(expenseRecordRepository, never()).save(any());
    }

    @Test
    void create_rejectsBlankNote() {
        assertThatThrownBy(() -> expenseService.create(10L, 1L, 100L, BigDecimal.TEN, " ", null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOTE_REQUIRED"));
    }

    @Test
    void create_rejectsWhenGroupDissolved() {
        // FR-018
        when(familyService.getGroup(10L)).thenReturn(Optional.of(dissolvedGroup()));

        assertThatThrownBy(() -> expenseService.create(10L, 1L, 100L, BigDecimal.TEN, "備註", null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("GROUP_DISSOLVED"));
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
        locked.setLockedByMemberId(2L);
        locked.setLockedAt(LocalDateTime.now());
        when(expenseRecordRepository.findById(1L)).thenReturn(Optional.of(locked));

        ExpenseRecord result = expenseService.lock(1L, 2L);

        assertThat(result.getLockedByMemberId()).isEqualTo(2L);
    }

    @Test
    void lock_rejectsWhenAlreadyLockedByOther() {
        // FR-027
        when(expenseRecordRepository.tryLock(eq(1L), eq(2L), any(), anyLong())).thenReturn(0);

        assertThatThrownBy(() -> expenseService.lock(1L, 2L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("RECORD_LOCKED"));
    }

    @Test
    void update_rejectsWhenCallerNotInGroup() {
        // FR-017、FR-019：非群組在職成員不可操作
        ExpenseRecord record = expenseRecord(1L, 10L, 100L);
        when(expenseRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(familyService.assertMemberAuthorized(10L, 5L, FamilyService.RequiredRole.ANY_MEMBER))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "NOT_GROUP_MEMBER", "您不是該家庭群組成員"));

        assertThatThrownBy(() -> expenseService.update(10L, 1L, 5L, 100L, BigDecimal.TEN, "備註", null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_GROUP_MEMBER"));
    }

    @Test
    void update_allowsRegularMemberEvenIfNotAuthor() {
        // FR-019：一般成員可編輯其他成員新增的紀錄
        ExpenseRecord record = expenseRecord(1L, 10L, 999L);
        record.setLockedByMemberId(2L);
        record.setLockedAt(LocalDateTime.now());
        when(expenseRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        FamilyMember regular = member(2L, 5L, MemberRole.MEMBER);
        when(familyService.assertMemberAuthorized(10L, 5L, FamilyService.RequiredRole.ANY_MEMBER)).thenReturn(regular);

        ExpenseRecord result = expenseService.update(10L, 1L, 5L, 200L, new BigDecimal("30"), "他人紀錄", null);

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
        when(familyService.assertMemberAuthorized(10L, 5L, FamilyService.RequiredRole.ANY_MEMBER)).thenReturn(regular);

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
        when(familyService.assertMemberAuthorized(10L, 5L, FamilyService.RequiredRole.ANY_MEMBER)).thenReturn(author);

        assertThatThrownBy(() -> expenseService.update(10L, 1L, 5L, 100L, BigDecimal.TEN, "備註", null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("LOCK_NOT_HELD_BY_CALLER"));
    }

    @Test
    void update_allowsAdminEvenIfNotAuthor() {
        // FR-019：管理者可編輯任何成員新增的支出紀錄
        ExpenseRecord record = expenseRecord(1L, 10L, 999L);
        record.setLockedByMemberId(1L);
        record.setLockedAt(LocalDateTime.now());
        when(expenseRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        FamilyMember admin = member(1L, 5L, MemberRole.ADMIN);
        when(familyService.assertMemberAuthorized(10L, 5L, FamilyService.RequiredRole.ANY_MEMBER)).thenReturn(admin);

        ExpenseRecord result = expenseService.update(10L, 1L, 5L, 200L, new BigDecimal("-500"), "更新後備註", null);

        assertThat(result.getAmount()).isEqualTo(-500);
        verify(expenseRecordRepository).releaseLock(1L);
    }

    private FamilyGroup activeGroup() {
        FamilyGroup group = new FamilyGroup("王家", FamilyGroupStatus.ACTIVE, "CODE1", LocalDateTime.now());
        group.setId(10L);
        return group;
    }

    private FamilyGroup dissolvedGroup() {
        FamilyGroup group = new FamilyGroup("王家", FamilyGroupStatus.DISSOLVED, "CODE1", LocalDateTime.now());
        group.setId(10L);
        return group;
    }

    private FamilyMember member(Long memberId, Long userId, MemberRole role) {
        FamilyMember member = new FamilyMember(10L, userId, MemberStatus.ACTIVE, role, LocalDateTime.now());
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
}
