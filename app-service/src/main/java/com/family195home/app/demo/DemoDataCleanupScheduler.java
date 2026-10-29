package com.family195home.app.demo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 僅在啟用 Demo 登入的環境執行；每小時清理一次過期 Demo 資料。 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.demo-login.enabled", havingValue = "true")
public class DemoDataCleanupScheduler {

    private final DemoDataCleanupService cleanupService;

    public DemoDataCleanupScheduler(DemoDataCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Taipei")
    public void run() {
        cleanupService.cleanup();
    }
}
