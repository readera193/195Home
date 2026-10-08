package com.family195home.notification.presentation;

import com.family195home.notification.infrastructure.client.AppServiceClient;
import com.family195home.notification.application.dto.AccountSummaryView;
import com.family195home.notification.application.dto.BindingResult;
import com.family195home.notification.application.dto.MonthlySummaryView;
import com.linecorp.bot.client.LineMessagingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LineWebhookControllerTest {

    private AppServiceClient appServiceClient;
    private LineWebhookController controller;

    @BeforeEach
    void setUp() {
        appServiceClient = mock(AppServiceClient.class);
        controller = new LineWebhookController(appServiceClient, mock(LineMessagingClient.class));
    }

    @Test
    void determineReply_parsesYearMonthFormatAndReturnsSummary() {
        // FR-014：YYYY-MM 格式正確解析
        when(appServiceClient.findByLineUserId("line-user-1"))
                .thenReturn(Optional.of(new BindingResult(1L, 10L)));
        when(appServiceClient.getMonthlySummary(10L, "2026-07"))
                .thenReturn(new MonthlySummaryView(10L, "2026-07", List.of(new AccountSummaryView(1L, "現金", -350)), -350));

        String reply = controller.determineReply("2026-07", "line-user-1");

        assertThat(reply).contains("2026-07").contains("現金").contains("-350");
    }

    @Test
    void determineReply_blocksQueryFromUnboundLineAccount() {
        // FR-015：未綁定帳號查詢阻擋，不洩漏任何家庭資料
        when(appServiceClient.findByLineUserId("line-user-unbound")).thenReturn(Optional.empty());

        String reply = controller.determineReply("2026-07", "line-user-unbound");

        assertThat(reply).contains("尚未綁定");
    }

    @Test
    void determineReply_repliesFormatHintForInvalidMessage() {
        // FR-014：不符合 YYYY-MM 格式的訊息回覆格式提示
        String reply = controller.determineReply("hello", "line-user-1");

        assertThat(reply).contains("YYYY-MM");
    }
}
