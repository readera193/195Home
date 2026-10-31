package com.family195home.app.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.family195home.app.expense.application.ExpenseService;
import com.family195home.app.expense.application.PaymentAccountService;
import com.family195home.app.family.application.FamilyService;
import com.family195home.app.family.application.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** 清理只協調各模組自己的刪除方法（constitution 原則 I），且須先刪參照方再刪被參照方。 */
class DemoDataCleanupServiceTest {

    private final FamilyService familyService = mock(FamilyService.class);
    private final UserService userService = mock(UserService.class);
    private final ExpenseService expenseService = mock(ExpenseService.class);
    private final PaymentAccountService paymentAccountService = mock(PaymentAccountService.class);

    private final DemoDataCleanupService cleanupService =
            new DemoDataCleanupService(
                    familyService,
                    userService,
                    expenseService,
                    paymentAccountService,
                    24,
                    Clock.systemDefaultZone());

    private final LocalDateTime cutoff = LocalDateTime.of(2026, 10, 30, 0, 0);

    @Test
    void cleanup_deletesExpensesThenAccountsThenGroupsThenOrphanUsers() {
        List<Long> groupIds = List.of(1L, 2L);
        when(familyService.findExpiredDemoGroupIds(cutoff, DemoDataCleanupService.BATCH_SIZE))
                .thenReturn(groupIds);

        int deleted = cleanupService.cleanup(cutoff);

        assertThat(deleted).isEqualTo(2);
        InOrder order = inOrder(expenseService, paymentAccountService, familyService, userService);
        order.verify(expenseService).purgeByFamilyGroupIds(groupIds);
        order.verify(paymentAccountService).purgeByFamilyGroupIds(groupIds);
        order.verify(familyService).purgeGroups(groupIds);
        order.verify(userService).purgeOrphanDemoUsers(cutoff);
    }

    @Test
    void cleanup_withNoExpiredGroups_onlyPurgesOrphanUsers() {
        when(familyService.findExpiredDemoGroupIds(cutoff, DemoDataCleanupService.BATCH_SIZE))
                .thenReturn(List.of());

        int deleted = cleanupService.cleanup(cutoff);

        assertThat(deleted).isZero();
        verify(expenseService, never()).purgeByFamilyGroupIds(anyList());
        verify(paymentAccountService, never()).purgeByFamilyGroupIds(anyList());
        verify(familyService, never()).purgeGroups(anyList());
        verify(userService).purgeOrphanDemoUsers(cutoff);
    }
}
