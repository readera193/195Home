package com.family195home.notification.presentation.scheduler;

import java.time.Clock;
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
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final long retryBackoffMs;
    private final Clock clock;

    public MonthlyNotificationScheduler(
            AppServiceClient appServiceClient,
            LineMessageSender lineMessageSender,
            NotificationLogRepository notificationLogRepository,
            @Value("${app.notification.max-retries}") int maxRetries,
            @Value("${app.notification.retry-backoff-ms:1000}") long retryBackoffMs,
            Clock clock) {
        this.appServiceClient = appServiceClient;
        this.lineMessageSender = lineMessageSender;
        this.notificationLogRepository = notificationLogRepository;
        this.maxRetries = maxRetries;
        this.retryBackoffMs = retryBackoffMs;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 23 L * ?", zone = "Asia/Taipei")
    public void sendMonthlySummaries() {
        String yearMonth = YearMonth.now(clock.withZone(ZoneId.of("Asia/Taipei"))).toString();
        List<LineBindingView> bindings = appServiceClient.findAllBindings();
        // 若某家庭當下無任何已綁定 LINE 的成員，該家庭不會出現在 bindings 中，等同自動略過（見 spec.md Edge Cases）
        // 同一家庭的多位成員共用同一份彙總，避免重複呼叫 app-service
        Map<Long, String> textByFamily = new HashMap<>();
        for (LineBindingView binding : bindings) {
            try {
                sendToOneMember(binding, yearMonth, textByFamily);
            } catch (Exception e) {
                // FR-013：任一成員的任何失敗（含取得彙總、寫入紀錄）都不得中斷其他成員
                log.error("月結通知處理失敗，familyMemberId={}", binding.familyMemberId(), e);
                recordFailure(binding, yearMonth, e.getMessage());
            }
        }
    }

    private void sendToOneMember(LineBindingView binding, String yearMonth, Map<Long, String> textByFamily) {
        String text = textByFamily.get(binding.familyGroupId());
        if (text == null) {
            MonthlySummaryView summary = appServiceClient.getMonthlySummary(binding.familyGroupId(), yearMonth);
            text = MonthlySummaryFormatter.format(summary);
            textByFamily.put(binding.familyGroupId(), text);
        }

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
                if (attempts < maxRetries) {
                    backoff(attempts);
                }
            }
        }
        // FR-013：達重試上限仍失敗則記錄錯誤並放棄，不影響其他已綁定成員
        saveLog(binding, yearMonth, NotificationStatus.FAILED, attempts, lastError != null ? lastError.getMessage() : "unknown error");
    }

    private void backoff(int attempt) {
        if (retryBackoffMs <= 0) {
            return;
        }
        try {
            Thread.sleep(retryBackoffMs * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void recordFailure(LineBindingView binding, String yearMonth, String errorMessage) {
        try {
            saveLog(binding, yearMonth, NotificationStatus.FAILED, 0, errorMessage);
        } catch (Exception e) {
            log.error("寫入月結通知失敗紀錄時發生錯誤，familyMemberId={}", binding.familyMemberId(), e);
        }
    }

    private void saveLog(LineBindingView binding, String yearMonth, NotificationStatus status, int attempts, String errorMessage) {
        NotificationLog logEntry = new NotificationLog(binding.familyGroupId(), binding.familyMemberId(), binding.lineUserId(), yearMonth);
        logEntry.setStatus(status);
        logEntry.setAttempts(attempts);
        logEntry.setLastAttemptAt(LocalDateTime.now(clock));
        logEntry.setErrorMessage(errorMessage);
        notificationLogRepository.save(logEntry);
    }
}
