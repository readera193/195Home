package com.family195home.notification.presentation.scheduler;

import com.family195home.notification.application.LineMessageSender;
import com.family195home.notification.application.NotificationLogRepository;
import com.family195home.notification.infrastructure.client.AppServiceClient;
import com.family195home.notification.domain.NotificationLog;
import com.family195home.notification.domain.NotificationStatus;
import com.family195home.notification.application.dto.LineBindingView;
import com.family195home.notification.application.dto.MonthlySummaryView;
import com.family195home.notification.application.MonthlySummaryFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * 每月最後一天 23:00 自動推播當月支出匯總（FR-013）。使用 Spring 內建 cron 的 {@code L}
 * 表示月最後一天（見 research.md 決策 8），不引入 Quartz 等額外排程框架。
 */
@Component
public class MonthlyNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonthlyNotificationScheduler.class);

    private final AppServiceClient appServiceClient;
    private final LineMessageSender lineMessageSender;
    private final NotificationLogRepository notificationLogRepository;
    private final int maxRetries;

    public MonthlyNotificationScheduler(
            AppServiceClient appServiceClient,
            LineMessageSender lineMessageSender,
            NotificationLogRepository notificationLogRepository,
            @Value("${app.notification.max-retries}") int maxRetries) {
        this.appServiceClient = appServiceClient;
        this.lineMessageSender = lineMessageSender;
        this.notificationLogRepository = notificationLogRepository;
        this.maxRetries = maxRetries;
    }

    @Scheduled(cron = "0 0 23 L * ?")
    public void sendMonthlySummaries() {
        String yearMonth = YearMonth.now().toString();
        List<LineBindingView> bindings = appServiceClient.findAllBindings();
        // 若某家庭當下無任何已綁定 LINE 的成員，該家庭不會出現在 bindings 中，等同自動略過（見 spec.md Edge Cases）
        for (LineBindingView binding : bindings) {
            sendToOneMember(binding, yearMonth);
        }
    }

    private void sendToOneMember(LineBindingView binding, String yearMonth) {
        MonthlySummaryView summary = appServiceClient.getMonthlySummary(binding.familyGroupId(), yearMonth);
        String text = MonthlySummaryFormatter.format(summary);

        int attempts = 0;
        Exception lastError = null;
        while (attempts < maxRetries) {
            attempts++;
            try {
                lineMessageSender.pushText(binding.lineUserId(), text);
                saveLog(binding, yearMonth, NotificationStatus.SUCCESS, attempts, null);
                return;
            } catch (Exception e) {
                lastError = e;
                log.warn("推播月結通知失敗（第 {} 次嘗試），familyMemberId={}", attempts, binding.familyMemberId(), e);
            }
        }
        // FR-013：達重試上限仍失敗則記錄錯誤並放棄，不影響其他已綁定成員
        saveLog(binding, yearMonth, NotificationStatus.FAILED, attempts, lastError != null ? lastError.getMessage() : "unknown error");
    }

    private void saveLog(LineBindingView binding, String yearMonth, NotificationStatus status, int attempts, String errorMessage) {
        NotificationLog logEntry = new NotificationLog(binding.familyGroupId(), binding.familyMemberId(), binding.lineUserId(), yearMonth);
        logEntry.setStatus(status);
        logEntry.setAttempts(attempts);
        logEntry.setLastAttemptAt(LocalDateTime.now());
        logEntry.setErrorMessage(errorMessage);
        notificationLogRepository.save(logEntry);
    }
}
