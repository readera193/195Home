package com.family195home.notification.presentation;

import com.family195home.notification.infrastructure.client.AppServiceClient;
import com.family195home.notification.infrastructure.client.AppServiceClientException;
import com.family195home.notification.application.dto.BindingResult;
import com.family195home.notification.application.dto.MonthlySummaryView;
import com.family195home.notification.application.MonthlySummaryFormatter;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * LINE Webhook 事件處理（US5、US6）。簽章驗證由 line-bot-spring-boot 自動處理
 * （見 research.md 決策 2）。呼叫 app-service 一律透過 {@link AppServiceClient}。
 */
@LineMessageHandler
public class LineWebhookController {

    private static final Pattern BINDING_CODE_PATTERN = Pattern.compile("\\d{6}");
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("\\d{4}-\\d{2}");

    private final AppServiceClient appServiceClient;
    private final LineMessagingClient lineMessagingClient;

    public LineWebhookController(AppServiceClient appServiceClient, LineMessagingClient lineMessagingClient) {
        this.appServiceClient = appServiceClient;
        this.lineMessagingClient = lineMessagingClient;
    }

    @EventMapping
    public void handleTextMessage(MessageEvent<TextMessageContent> event) {
        String text = event.getMessage().getText().trim();
        String lineUserId = event.getSource().getUserId();
        reply(event.getReplyToken(), determineReply(text, lineUserId));
    }

    /** 依訊息內容決定回覆文字：6 碼綁定碼 → 完成綁定；YYYY-MM → 月結查詢；其餘 → 格式提示（FR-014）。 */
    public String determineReply(String text, String lineUserId) {
        if (BINDING_CODE_PATTERN.matcher(text).matches()) {
            // FR-023
            return handleBindingCode(text, lineUserId);
        }
        return handleOtherMessage(text, lineUserId);
    }

    @EventMapping
    public void handleDefault(Event event) {
        // 忽略非文字訊息事件（follow/unfollow/join 等）
    }

    private String handleBindingCode(String code, String lineUserId) {
        try {
            appServiceClient.consumeBindingCode(code, lineUserId);
            return "綁定成功！之後可傳送「YYYY-MM」（例如 2026-07）查詢該月支出匯總，或等待每月最後一天 23:00 自動通知。";
        } catch (AppServiceClientException e) {
            if ("CODE_EXPIRED_OR_USED".equals(e.getErrorCode())) {
                return "綁定碼已過期或已使用，請重新登入平台產生新碼。";
            }
            if ("LINE_ACCOUNT_ALREADY_BOUND".equals(e.getErrorCode())) {
                return "此 LINE 帳號已綁定其他家庭成員身分。";
            }
            return "綁定失敗，請稍後再試。";
        }
    }

    private String handleOtherMessage(String text, String lineUserId) {
        if (YEAR_MONTH_PATTERN.matcher(text).matches()) {
            // FR-014, FR-015
            return handleMonthQuery(text, lineUserId);
        }
        // FR-014：不符合 YYYY-MM 格式的訊息一律回覆格式提示，不視為有效查詢
        return "請傳送平台產生的 6 碼綁定碼完成綁定，或以「YYYY-MM」格式（例如 2026-07）查詢指定月份支出匯總。";
    }

    private String handleMonthQuery(String month, String lineUserId) {
        Optional<BindingResult> binding = appServiceClient.findByLineUserId(lineUserId);
        if (binding.isEmpty()) {
            // FR-015：未綁定帳號不得取得任何家庭支出資料
            return "此帳號尚未綁定家庭成員身分，請先於平台登入後產生綁定碼並在此輸入完成綁定。";
        }
        MonthlySummaryView summary = appServiceClient.getMonthlySummary(binding.get().familyGroupId(), month);
        return MonthlySummaryFormatter.format(summary);
    }

    private void reply(String replyToken, String text) {
        try {
            lineMessagingClient.replyMessage(new ReplyMessage(replyToken, List.of(new TextMessage(text)))).get();
        } catch (Exception e) {
            // 回覆失敗不影響 Webhook 本身回應 LINE 平台（見 contracts/notification-service.md）
        }
    }
}
