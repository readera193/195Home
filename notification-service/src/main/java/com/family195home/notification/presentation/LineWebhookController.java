package com.family195home.notification.presentation;

import com.family195home.notification.infrastructure.client.AppServiceClient;
import com.family195home.notification.application.dto.BindingResult;
import com.family195home.notification.application.dto.MonthlySummaryView;
import com.family195home.notification.application.MonthlySummaryFormatter;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.PostbackEvent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * LINE Webhook 事件處理（US5、US6）。簽章驗證由 line-bot-spring-boot 自動處理
 * （見 research.md 決策 2）。呼叫 app-service 一律透過 {@link AppServiceClient}。
 */
@LineMessageHandler
public class LineWebhookController {

    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("\\d{4}-\\d{2}");
    private static final String FORMAT_HINT = "請以「YYYY-MM」格式（例如 2026-07）查詢指定月份支出匯總。";

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

    /** 依訊息內容決定回覆文字：YYYY-MM → 月結查詢；其餘 → 格式提示（FR-014）。 */
    public String determineReply(String text, String lineUserId) {
        if (YEAR_MONTH_PATTERN.matcher(text).matches()) {
            // FR-014, FR-015
            return handleMonthQuery(text, lineUserId);
        }
        // FR-014：不符合 YYYY-MM 格式的訊息一律回覆格式提示，不視為有效查詢
        return FORMAT_HINT;
    }

    /** Datetime Picker / Rich Menu 的 postback：取選到日期的年月（params.date），或 data 本身即 YYYY-MM。 */
    @EventMapping
    public void handlePostback(PostbackEvent event) {
        String month = extractMonth(event.getPostbackContent().getData(), event.getPostbackContent().getParams(),
                YearMonth.now(ZoneId.of("Asia/Taipei")));
        String lineUserId = event.getSource().getUserId();
        reply(event.getReplyToken(), month == null ? FORMAT_HINT : determineReply(month, lineUserId));
    }

    /**
     * Rich Menu 為固定內容，「本月」「上月」以 data 為 {@code month=current}／{@code month=previous} 表示，
     * 由此換算成實際月份；Datetime Picker 則由 params.date 取年月。
     */
    static String extractMonth(String data, Map<String, String> params, YearMonth now) {
        String date = params == null ? null : params.get("date");
        if (date != null && date.length() >= 7) {
            return date.substring(0, 7);
        }
        if (data == null) {
            return null;
        }
        String trimmed = data.trim();
        return switch (trimmed) {
            case "month=current" -> now.toString();
            case "month=previous" -> now.minusMonths(1).toString();
            default -> YEAR_MONTH_PATTERN.matcher(trimmed).matches() ? trimmed : null;
        };
    }

    @EventMapping
    public void handleDefault(Event event) {
        // 忽略非文字訊息事件（follow/unfollow/join 等）
    }

    private String handleMonthQuery(String month, String lineUserId) {
        if (!isValidYearMonth(month)) {
            return FORMAT_HINT;
        }
        try {
            return queryMonthlySummary(month, lineUserId);
        } catch (RuntimeException e) {
            // app-service 呼叫失敗時仍回覆可讀訊息，避免使用者收不到任何回應（FR-014）
            return "目前無法查詢支出匯總，請稍後再試。";
        }
    }

    private static boolean isValidYearMonth(String month) {
        try {
            YearMonth.parse(month);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private String queryMonthlySummary(String month, String lineUserId) {
        Optional<BindingResult> binding = appServiceClient.findByLineUserId(lineUserId);
        if (binding.isEmpty()) {
            // FR-015：未加入任何家庭的帳號不得取得任何家庭支出資料
            return "此帳號尚未加入任何家庭，請先至平台以 LINE 登入並建立或加入家庭。";
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
