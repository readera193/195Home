package com.family195home.app.demo;

import com.family195home.app.family.application.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 清除超過保留時間的 Demo 資料（群組、成員、支付帳戶、支出紀錄與 Demo 帳號），避免公開展示環境資料無限成長。
 * 每次最多處理 {@link #BATCH_SIZE} 個群組，剩下的留給下一輪，避免單一交易過大。
 */
@Service
public class DemoDataCleanupService {

    static final int BATCH_SIZE = 50;

    private static final Logger log = LoggerFactory.getLogger(DemoDataCleanupService.class);

    private final DemoDataCleanupMapper mapper;
    private final long retentionHours;

    public DemoDataCleanupService(
            DemoDataCleanupMapper mapper, @Value("${app.demo-login.retention-hours:24}") long retentionHours) {
        this.mapper = mapper;
        this.retentionHours = retentionHours;
    }

    /** @return 本次刪除的群組數 */
    @Transactional
    public int cleanup() {
        return cleanup(LocalDateTime.now().minusHours(retentionHours));
    }

    @Transactional
    public int cleanup(LocalDateTime cutoff) {
        String prefix = UserService.DEMO_LINE_USER_ID_PREFIX;
        List<Long> groupIds = mapper.selectExpiredDemoGroupIds(prefix, cutoff, BATCH_SIZE);
        if (!groupIds.isEmpty()) {
            int expenses = mapper.deleteExpensesByGroupIds(groupIds);
            mapper.deletePaymentAccountsByGroupIds(groupIds);
            mapper.deleteMembersByGroupIds(groupIds);
            mapper.deleteGroupsByIds(groupIds);
            log.info("已清除過期 Demo 群組 {} 個（支出紀錄 {} 筆）", groupIds.size(), expenses);
        }
        int users = mapper.deleteOrphanDemoUsers(prefix, cutoff);
        if (users > 0) {
            log.info("已清除過期 Demo 帳號 {} 個", users);
        }
        return groupIds.size();
    }
}
