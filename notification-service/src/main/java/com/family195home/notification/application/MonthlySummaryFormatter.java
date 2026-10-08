package com.family195home.notification.application;

import com.family195home.notification.application.dto.MonthlySummaryView;

/** LINE 回覆/推播訊息共用的月結彙總文字格式化（供 Webhook 查詢與每月排程通知共用）。 */
public final class MonthlySummaryFormatter {

    private MonthlySummaryFormatter() {
    }

    public static String format(MonthlySummaryView summary) {
        StringBuilder sb = new StringBuilder();
        sb.append(summary.month()).append(" 家庭支出匯總\n");
        if (summary.accounts().isEmpty()) {
            sb.append("本月尚無任何支出紀錄，淨額為 0");
        } else {
            for (var account : summary.accounts()) {
                sb.append(account.paymentAccountName()).append("：").append(account.netAmount()).append("\n");
            }
            sb.append("總計：").append(summary.totalNetAmount());
        }
        return sb.toString();
    }
}
