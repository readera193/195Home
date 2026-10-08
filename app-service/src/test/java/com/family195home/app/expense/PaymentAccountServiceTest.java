package com.family195home.app.expense;

import com.family195home.app.common.ApiException;
import com.family195home.app.common.ErrorKind;
import com.family195home.app.expense.application.ExpenseRecordRepository;
import com.family195home.app.expense.application.PaymentAccountRepository;
import com.family195home.app.expense.domain.PaymentAccount;
import com.family195home.app.expense.domain.PaymentAccountStatus;
import com.family195home.app.expense.service.PaymentAccountService;
import com.family195home.app.family.domain.FamilyGroup;
import com.family195home.app.family.domain.FamilyGroupStatus;
import com.family195home.app.family.domain.FamilyMember;
import com.family195home.app.family.domain.MemberRole;
import com.family195home.app.family.domain.MemberStatus;
import com.family195home.app.family.service.FamilyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentAccountServiceTest {

    private PaymentAccountRepository paymentAccountRepository;
    private ExpenseRecordRepository expenseRecordRepository;
    private FamilyService familyService;
    private PaymentAccountService service;

    @BeforeEach
    void setUp() {
        paymentAccountRepository = mock(PaymentAccountRepository.class);
        expenseRecordRepository = mock(ExpenseRecordRepository.class);
        familyService = mock(FamilyService.class);
        service = new PaymentAccountService(paymentAccountRepository, expenseRecordRepository, familyService);
    }

    @Test
    void create_rejectsRegularMember() {
        // FR-003：一般成員不可建立支付帳戶
        when(familyService.assertMemberAuthorized(10L, 5L, FamilyService.RequiredRole.ADMIN))
                .thenThrow(new ApiException(ErrorKind.FORBIDDEN, "NOT_GROUP_ADMIN", "僅群組管理者可執行此操作"));

        assertThatThrownBy(() -> service.create(10L, 5L, "現金"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_GROUP_ADMIN"));
        verify(paymentAccountRepository, never()).save(any());
    }

    @Test
    void create_byAdmin_savesAccountWithAdminAsCreator() {
        FamilyMember admin = member(1L, MemberRole.ADMIN);
        when(familyService.assertMemberAuthorized(10L, 5L, FamilyService.RequiredRole.ADMIN)).thenReturn(admin);
        FamilyGroup group = new FamilyGroup("王家", FamilyGroupStatus.ACTIVE, "CODE1", LocalDateTime.now());
        when(familyService.getGroup(10L)).thenReturn(Optional.of(group));

        PaymentAccount result = service.create(10L, 5L, "現金");

        assertThat(result.getFamilyMemberId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(PaymentAccountStatus.ACTIVE);
        verify(paymentAccountRepository).save(result);
    }

    @Test
    void create_rejectsDissolvedGroup() {
        // FR-018
        when(familyService.assertMemberAuthorized(10L, 5L, FamilyService.RequiredRole.ADMIN))
                .thenReturn(member(1L, MemberRole.ADMIN));
        FamilyGroup group = new FamilyGroup("王家", FamilyGroupStatus.DISSOLVED, "CODE1", LocalDateTime.now());
        when(familyService.getGroup(10L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> service.create(10L, 5L, "現金"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("GROUP_DISSOLVED"));
    }

    @Test
    void rename_byAdmin_updatesName() {
        stubAccount(3L, 10L);
        when(familyService.assertMemberAuthorized(10L, 5L, FamilyService.RequiredRole.ADMIN))
                .thenReturn(member(1L, MemberRole.ADMIN));

        PaymentAccount result = service.rename(3L, 5L, "銀行帳戶");

        assertThat(result.getName()).isEqualTo("銀行帳戶");
        verify(paymentAccountRepository).update(result);
    }

    @Test
    void rename_rejectsRegularMember() {
        stubAccount(3L, 10L);
        when(familyService.assertMemberAuthorized(10L, 5L, FamilyService.RequiredRole.ADMIN))
                .thenThrow(new ApiException(ErrorKind.FORBIDDEN, "NOT_GROUP_ADMIN", "僅群組管理者可執行此操作"));

        assertThatThrownBy(() -> service.rename(3L, 5L, "銀行帳戶"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_GROUP_ADMIN"));
        verify(paymentAccountRepository, never()).update(any());
    }

    @Test
    void disable_byAdmin_softDisables() {
        // FR-022
        stubAccount(3L, 10L);
        when(familyService.assertMemberAuthorized(10L, 5L, FamilyService.RequiredRole.ADMIN))
                .thenReturn(member(1L, MemberRole.ADMIN));

        PaymentAccount result = service.disable(3L, 5L);

        assertThat(result.getStatus()).isEqualTo(PaymentAccountStatus.DISABLED);
    }

    @Test
    void disable_rejectsRegularMember() {
        stubAccount(3L, 10L);
        when(familyService.assertMemberAuthorized(10L, 5L, FamilyService.RequiredRole.ADMIN))
                .thenThrow(new ApiException(ErrorKind.FORBIDDEN, "NOT_GROUP_ADMIN", "僅群組管理者可執行此操作"));

        assertThatThrownBy(() -> service.disable(3L, 5L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_GROUP_ADMIN"));
    }

    @Test
    void delete_unusedAccount_isDeleted() {
        // FR-022：從未被使用的帳戶可真正刪除
        stubAccount(3L, 10L);
        when(familyService.assertMemberAuthorized(10L, 5L, FamilyService.RequiredRole.ADMIN))
                .thenReturn(member(1L, MemberRole.ADMIN));
        when(expenseRecordRepository.existsByPaymentAccountId(3L)).thenReturn(false);

        service.delete(3L, 5L);

        verify(paymentAccountRepository).delete(3L);
    }

    @Test
    void delete_usedAccount_isRejected() {
        // FR-022：已被使用過的帳戶不可真正刪除
        stubAccount(3L, 10L);
        when(familyService.assertMemberAuthorized(10L, 5L, FamilyService.RequiredRole.ADMIN))
                .thenReturn(member(1L, MemberRole.ADMIN));
        when(expenseRecordRepository.existsByPaymentAccountId(3L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(3L, 5L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("ACCOUNT_IN_USE"));
        verify(paymentAccountRepository, never()).delete(any());
    }

    private void stubAccount(Long accountId, Long groupId) {
        PaymentAccount account = new PaymentAccount(1L, groupId, "現金", PaymentAccountStatus.ACTIVE, LocalDateTime.now());
        account.setId(accountId);
        when(paymentAccountRepository.findById(accountId)).thenReturn(Optional.of(account));
    }

    private FamilyMember member(Long id, MemberRole role) {
        FamilyMember member = new FamilyMember(10L, id * 100, MemberStatus.ACTIVE, role, LocalDateTime.now());
        member.setId(id);
        return member;
    }
}
