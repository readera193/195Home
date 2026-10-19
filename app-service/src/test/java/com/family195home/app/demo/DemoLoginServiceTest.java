package com.family195home.app.demo;

import com.family195home.app.expense.application.ExpenseService;
import com.family195home.app.expense.application.PaymentAccountService;
import com.family195home.app.expense.domain.PaymentAccount;
import com.family195home.app.family.application.FamilyService;
import com.family195home.app.family.application.UserService;
import com.family195home.app.family.domain.FamilyGroup;
import com.family195home.app.family.domain.FamilyMember;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
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
                .thenReturn(new UserService.LoginResult("jwt", 7L, "Demo-ab12", Instant.now()));
        FamilyGroup group = new FamilyGroup();
        group.setId(20L);
        when(familyService.createGroup(7L, "Demo-ab12 的家")).thenReturn(group);
        FamilyMember member = new FamilyMember();
        member.setId(30L);
        when(familyService.getMyMembership(7L)).thenReturn(Optional.of(member));
        when(accountService.create(eq(20L), eq(7L), any())).thenAnswer(inv -> {
            PaymentAccount account = new PaymentAccount();
            account.setId(100L + inv.getArgument(2).hashCode() % 1000);
            return account;
        });

        UserService.LoginResult result = new DemoLoginService(userService, familyService, accountService, expenseService).login();

        assertThat(result.token()).isEqualTo("jwt");
        verify(accountService, times(3)).create(eq(20L), eq(7L), any());
        verify(expenseService, atLeast(10)).create(
                eq(20L), eq(30L), anyLong(), any(BigDecimal.class), any(String.class), any(LocalDateTime.class));
    }
}
