package com.family195home.app.demo;

import com.family195home.app.expense.application.ExpenseService;
import com.family195home.app.expense.application.PaymentAccountService;
import com.family195home.app.family.application.FamilyService;
import com.family195home.app.family.application.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 清除超過保留時間的 Demo 資料（群組、成員、支付帳戶、支出紀錄與 Demo 帳號），避免公開展示環境資料無限成長。 每個模組只刪除自己的資料表（constitution 原則
 * I），本類別僅負責協調呼叫順序：先刪參照方（expense），再刪被參照方（family）。 每次最多處理 {@link #BATCH_SIZE} 個群組，剩下的留給下一輪，避免單一交易過大。
 */
@Service
public class DemoDataCleanupService {

    static final int BATCH_SIZE = 50;

    private static final Logger log = LoggerFactory.getLogger(DemoDataCleanupService.class);

    private final FamilyService familyService;
    private final UserService userService;
    private final ExpenseService expenseService;
    private final PaymentAccountService paymentAccountService;
    private final long retentionHours;
    private final Clock clock;

    public DemoDataCleanupService(
            FamilyService familyService,
            UserService userService,
            ExpenseService expenseService,
            PaymentAccountService paymentAccountService,
            @Value("${app.demo-login.retention-hours:24}") long retentionHours,
            Clock clock) {
        this.familyService = familyService;
        this.userService = userService;
        this.expenseService = expenseService;
        this.paymentAccountService = paymentAccountService;
        this.retentionHours = retentionHours;
        this.clock = clock;
    }

    /**
     * @return 本次刪除的群組數
     */
    @Transactional
    public int cleanup() {
        return cleanup(LocalDateTime.now(clock).minusHours(retentionHours));
    }

    @Transactional
    public int cleanup(LocalDateTime cutoff) {
        List<Long> groupIds = familyService.findExpiredDemoGroupIds(cutoff, BATCH_SIZE);
        if (!groupIds.isEmpty()) {
            int expenses = expenseService.purgeByFamilyGroupIds(groupIds);
            paymentAccountService.purgeByFamilyGroupIds(groupIds);
            familyService.purgeGroups(groupIds);
            log.info("已清除過期 Demo 群組 {} 個（支出紀錄 {} 筆）", groupIds.size(), expenses);
        }
        int users = userService.purgeOrphanDemoUsers(cutoff);
        if (users > 0) {
            log.info("已清除過期 Demo 帳號 {} 個", users);
        }
        return groupIds.size();
    }
}
