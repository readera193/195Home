package com.family195home.notification.presentation.scheduler;

import java.time.Clock;
import com.family195home.notification.application.LineMessageSendException;
import com.family195home.notification.application.LineMessageSender;
import com.family195home.notification.application.NotificationLogRepository;
import com.family195home.notification.infrastructure.client.AppServiceClient;
import com.family195home.notification.domain.NotificationLog;
import com.family195home.notification.domain.NotificationStatus;
import com.family195home.notification.application.dto.AccountSummaryView;
import com.family195home.notification.application.dto.LineBindingView;
import com.family195home.notification.application.dto.MonthlySummaryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MonthlyNotificationSchedulerTest {

    private AppServiceClient appServiceClient;
    private LineMessageSender lineMessageSender;
    private NotificationLogRepository notificationLogRepository;
    private MonthlyNotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        appServiceClient = mock(AppServiceClient.class);
        lineMessageSender = mock(LineMessageSender.class);
        notificationLogRepository = mock(NotificationLogRepository.class);
        scheduler = new MonthlyNotificationScheduler(appServiceClient, lineMessageSender, notificationLogRepository, 3, 0, Clock.systemDefaultZone());

        when(appServiceClient.getMonthlySummary(any(), any())).thenReturn(
                new MonthlySummaryView(10L, "2026-09", List.of(new AccountSummaryView(1L, "現金", -350)), -350));
    }

    @Test
    void sendMonthlySummaries_isScheduledAtLastDay2300InTaipeiTime() throws NoSuchMethodException {
        // FR-013：容器預設為 UTC，排程必須明確指定台灣時區
        Scheduled scheduled = MonthlyNotificationScheduler.class
                .getMethod("sendMonthlySummaries").getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 0 23 L * ?");
        assertThat(scheduled.zone()).isEqualTo("Asia/Taipei");
    }

    @Test
    void sendMonthlySummaries_marksFailedAfterExhaustingRetries() {
        // FR-013：達重試上限（3 次）仍失敗則記錄錯誤並放棄
        when(appServiceClient.findAllBindings()).thenReturn(List.of(new LineBindingView(1L, 10L, "line-user-1")));
        doThrow(new LineMessageSendException("LINE API 異常", null)).when(lineMessageSender).pushText(any(), any());

        scheduler.sendMonthlySummaries();

        verify(lineMessageSender, times(3)).pushText(any(), any());
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(captor.getValue().getAttempts()).isEqualTo(3);
    }

    @Test
    void sendMonthlySummaries_oneMemberFailureDoesNotAffectOthers() {
        // FR-013：某成員發送失敗不影響其他已綁定成員正常收到通知
        LineBindingView failing = new LineBindingView(1L, 10L, "line-user-fail");
        LineBindingView succeeding = new LineBindingView(2L, 20L, "line-user-ok");
        when(appServiceClient.findAllBindings()).thenReturn(List.of(failing, succeeding));
        doThrow(new LineMessageSendException("boom", null)).when(lineMessageSender).pushText(eq("line-user-fail"), any());

        scheduler.sendMonthlySummaries();

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(captor.capture());
        List<NotificationLog> logs = captor.getAllValues();
        assertThat(logs).extracting(NotificationLog::getStatus)
                .containsExactlyInAnyOrder(NotificationStatus.FAILED, NotificationStatus.SUCCESS);
    }

    @Test
    void sendMonthlySummaries_summaryFailureDoesNotAbortRemainingMembers() {
        // 第一位成員的家庭彙總取得失敗，第二位（另一個家庭）仍須送出
        LineBindingView first = new LineBindingView(1L, 10L, "line-user-1");
        LineBindingView second = new LineBindingView(2L, 20L, "line-user-2");
        when(appServiceClient.findAllBindings()).thenReturn(List.of(first, second));
        when(appServiceClient.getMonthlySummary(eq(10L), any())).thenThrow(new RuntimeException("app-service down"));

        scheduler.sendMonthlySummaries();

        verify(lineMessageSender).pushText(eq("line-user-2"), any());
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(NotificationLog::getStatus)
                .containsExactlyInAnyOrder(NotificationStatus.FAILED, NotificationStatus.SUCCESS);
    }

    @Test
    void sendMonthlySummaries_sameFamilyFetchesSummaryOnce() {
        when(appServiceClient.findAllBindings()).thenReturn(List.of(
                new LineBindingView(1L, 10L, "line-user-1"), new LineBindingView(2L, 10L, "line-user-2")));

        scheduler.sendMonthlySummaries();

        verify(appServiceClient, times(1)).getMonthlySummary(eq(10L), any());
        verify(lineMessageSender, times(2)).pushText(any(), any());
    }

}
