package com.family195home.notification.controller;

import com.family195home.notification.client.AppServiceClient;
import com.family195home.notification.client.AppServiceClientException;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;

import java.util.List;
import java.util.regex.Pattern;

/**
 * LINE Webhook 事件處理（US5、US6）。簽章驗證由 line-bot-spring-boot 自動處理
 * （見 research.md 決策 2）。呼叫 app-service 一律透過 {@link AppServiceClient}。
 */
@LineMessageHandler
public class LineWebhookController {

    private static final Pattern BINDING_CODE_PATTERN = Pattern.compile("\\d{6}");

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

        String replyText;
        if (BINDING_CODE_PATTERN.matcher(text).matches()) {
            // FR-023
            replyText = handleBindingCode(text, lineUserId);
        } else {
            replyText = handleOtherMessage(text, lineUserId);
        }
        reply(event.getReplyToken(), replyText);
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

    /** 由 US6（T061/T062）擴充：YYYY-MM 月份查詢與格式提示。 */
    protected String handleOtherMessage(String text, String lineUserId) {
        return "請傳送平台產生的 6 碼綁定碼完成綁定，或以「YYYY-MM」格式（例如 2026-07）查詢指定月份支出匯總。";
    }

    private void reply(String replyToken, String text) {
        try {
            lineMessagingClient.replyMessage(new ReplyMessage(replyToken, List.of(new TextMessage(text)))).get();
        } catch (Exception e) {
            // 回覆失敗不影響 Webhook 本身回應 LINE 平台（見 contracts/notification-service.md）
        }
    }
}
