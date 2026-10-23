package com.family195home.app.demo;

import com.family195home.app.expense.application.ExpenseService;
import com.family195home.app.expense.application.PaymentAccountService;
import com.family195home.app.expense.domain.PaymentAccount;
import com.family195home.app.family.application.FamilyService;
import com.family195home.app.family.application.UserService;
import com.family195home.app.family.domain.FamilyGroup;
import com.family195home.app.family.domain.FamilyMember;
import com.family195home.app.family.domain.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoLoginServiceTest {

    @Test
    void login_createsGroupAccountsAndSampleExpenses() {
        UserService userService = mock(UserService.class);
        FamilyService familyService = mock(FamilyService.class);
        PaymentAccountService accountService = mock(PaymentAccountService.class);
        ExpenseService expenseService = mock(ExpenseService.class);

        when(userService.loginAsDemo())
                .thenReturn(new UserService.LoginResult("jwt", 7L, "使用者", Instant.now()));
        FamilyGroup group = new FamilyGroup();
        group.setId(20L);
        when(familyService.createGroup(7L, "使用者的家 #7")).thenReturn(group);
        FamilyMember member = new FamilyMember();
        member.setId(30L);
        when(familyService.getMyMembership(7L)).thenReturn(Optional.of(member));
        group.setInviteCode("INVITE");
        AtomicLong nextUserId = new AtomicLong(200L);
        when(userService.createDemoMember(any())).thenAnswer(inv -> {
            User user = new User();
            user.setId(nextUserId.getAndIncrement());
            return user;
        });
        when(familyService.joinGroup(anyLong(), eq("INVITE"))).thenAnswer(inv -> {
            FamilyMember joined = new FamilyMember();
            joined.setId(inv.<Long>getArgument(0) + 1000L);
            return joined;
        });
        when(accountService.create(eq(20L), eq(7L), any())).thenAnswer(inv -> {
            PaymentAccount account = new PaymentAccount();
            account.setId(100L + inv.getArgument(2).hashCode() % 1000);
            return account;
        });

        UserService.LoginResult result = new DemoLoginService(userService, familyService, accountService, expenseService).login();

        assertThat(result.token()).isEqualTo("jwt");
        verify(accountService, times(5)).create(eq(20L), eq(7L), any());
        verify(userService, times(4)).createDemoMember(any());
        verify(familyService, times(4)).joinGroup(anyLong(), eq("INVITE"));
        // 上月 1 日到今天，每天 0–5 筆
        LocalDate today = LocalDate.now();
        int days = today.minusMonths(1).lengthOfMonth() + today.getDayOfMonth();
        verify(expenseService, atLeast(1)).create(
                eq(20L), anyLong(), anyLong(), any(BigDecimal.class), any(String.class), any(LocalDateTime.class));
        verify(expenseService, atMost(days * 5)).create(
                eq(20L), anyLong(), anyLong(), any(BigDecimal.class), any(String.class), any(LocalDateTime.class));
    }
}
