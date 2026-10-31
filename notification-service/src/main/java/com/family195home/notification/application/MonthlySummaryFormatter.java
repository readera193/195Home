package com.family195home.notification.application;

import com.family195home.notification.application.dto.MonthlySummaryView;
import java.util.Locale;

/** LINE 回覆/推播訊息共用的月結彙總文字格式化（供 Webhook 查詢與每月排程通知共用）。 */
public final class MonthlySummaryFormatter {

    private MonthlySummaryFormatter() {}

    public static String format(MonthlySummaryView summary) {
        StringBuilder sb = new StringBuilder();
        sb.append(summary.month()).append(" 家庭支出匯總\n");
        if (summary.accounts().isEmpty()) {
            sb.append("本月尚無任何支出紀錄，淨額為 0");
        } else {
            for (var account : summary.accounts()) {
                sb.append(account.paymentAccountName())
                        .append("：")
                        .append(formatAmount(account.netAmount()))
                        .append("\n");
            }
            sb.append("總計：").append(formatAmount(summary.totalNetAmount()));
        }
        return sb.toString();
    }

    /** 金額加千分位；固定 Locale.ROOT，避免伺服器預設語系改變分隔符號。 */
    private static String formatAmount(long amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }
}
