package com.family195home.notification;

import com.family195home.notification.application.LineMessageSendException;
import com.family195home.notification.application.LineMessageSender;
import com.family195home.notification.application.NotificationLogRepository;
import com.family195home.notification.client.AppServiceClient;
import com.family195home.notification.domain.NotificationLog;
import com.family195home.notification.domain.NotificationStatus;
import com.family195home.notification.dto.AccountSummaryView;
import com.family195home.notification.dto.LineBindingView;
import com.family195home.notification.dto.MonthlySummaryView;
import com.family195home.notification.scheduler.MonthlyNotificationScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        scheduler = new MonthlyNotificationScheduler(appServiceClient, lineMessageSender, notificationLogRepository, 3);

        when(appServiceClient.getMonthlySummary(any(), any())).thenReturn(
                new MonthlySummaryView(10L, "2026-09", List.of(new AccountSummaryView(1L, "現金", -350)), -350));
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

}
