package com.family195home.app.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.family195home.app.expense.application.ExpenseService;
import com.family195home.app.expense.application.PaymentAccountService;
import com.family195home.app.expense.domain.PaymentAccount;
import com.family195home.app.family.application.FamilyService;
import com.family195home.app.family.application.UserService;
import com.family195home.app.family.domain.FamilyGroup;
import com.family195home.app.family.domain.FamilyMember;
import com.family195home.app.family.domain.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        member.setUserId(7L);
        when(familyService.getMyMembership(7L)).thenReturn(Optional.of(member));
        group.setInviteCode("INVITE");
        AtomicLong nextUserId = new AtomicLong(200L);
        when(userService.createDemoMember(any()))
                .thenAnswer(
                        inv -> {
                            User user = new User();
                            user.setId(nextUserId.getAndIncrement());
                            return user;
                        });
        when(familyService.joinGroup(anyLong(), eq("INVITE")))
                .thenAnswer(
                        inv -> {
                            FamilyMember joined = new FamilyMember();
                            joined.setId(inv.<Long>getArgument(0) + 1000L);
                            joined.setUserId(inv.<Long>getArgument(0));
                            return joined;
                        });
        when(accountService.create(eq(20L), eq(7L), any()))
                .thenAnswer(
                        inv -> {
                            PaymentAccount account = new PaymentAccount();
                            account.setId(100L + inv.getArgument(2).hashCode() % 1000);
                            return account;
                        });

        // 固定在月初 1 日凌晨：隨機產生的本月資料最容易為空的情境
        ZoneId zone = ZoneId.of("Asia/Taipei");
        LocalDate today = LocalDate.of(2026, 11, 1);
        Clock clock = Clock.fixed(today.atTime(0, 30).atZone(zone).toInstant(), zone);

        UserService.LoginResult result =
                new DemoLoginService(
                                userService, familyService, accountService, expenseService, clock)
                        .login();

        assertThat(result.token()).isEqualTo("jwt");
        verify(accountService, times(5)).create(eq(20L), eq(7L), any());
        verify(userService, times(4)).createDemoMember(any());
        verify(familyService, times(4)).joinGroup(anyLong(), eq("INVITE"));
        // 上月 1 日到今天，每天 0–5 筆，另加本月保證的 3 筆
        int days = today.minusMonths(1).lengthOfMonth() + today.getDayOfMonth();
        ArgumentCaptor<Long> accountIds = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<LocalDateTime> occurredAts = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(expenseService, atMost(days * 5 + 3))
                .create(
                        eq(20L),
                        anyLong(),
                        accountIds.capture(),
                        any(BigDecimal.class),
                        any(String.class),
                        occurredAts.capture());

        // 即使是月初，本月也至少有 3 筆、分屬 3 個不同帳戶的紀錄，且不會是未來時間
        YearMonth thisMonth = YearMonth.from(today);
        LocalDateTime now = LocalDateTime.now(clock);
        Set<Long> currentMonthAccounts = new HashSet<>();
        int currentMonthCount = 0;
        for (int i = 0; i < occurredAts.getAllValues().size(); i++) {
            LocalDateTime occurredAt = occurredAts.getAllValues().get(i);
            assertThat(occurredAt).isBeforeOrEqualTo(now);
            if (YearMonth.from(occurredAt).equals(thisMonth)) {
                currentMonthCount++;
                currentMonthAccounts.add(accountIds.getAllValues().get(i));
            }
        }
        assertThat(currentMonthCount).isGreaterThanOrEqualTo(3);
        assertThat(currentMonthAccounts).hasSizeGreaterThanOrEqualTo(3);
    }
}
