package com.family195home.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.family195home.notification.application.dto.AccountSummaryView;
import com.family195home.notification.application.dto.MonthlySummaryView;
import java.util.List;
import org.junit.jupiter.api.Test;

class MonthlySummaryFormatterTest {

    @Test
    void formatsAmountsWithThousandsSeparator() {
        MonthlySummaryView summary =
                new MonthlySummaryView(
                        1L,
                        "2026-10",
                        List.of(
                                new AccountSummaryView(1L, "信用卡", 1234567),
                                new AccountSummaryView(2L, "現金", -2500),
                                new AccountSummaryView(3L, "悠遊卡", 999)),
                        1233066);

        assertThat(MonthlySummaryFormatter.format(summary))
                .isEqualTo(
                        "2026-10 家庭支出匯總\n"
                                + "信用卡：1,234,567\n"
                                + "現金：-2,500\n"
                                + "悠遊卡：999\n"
                                + "總計：1,233,066");
    }

    @Test
    void formatsZeroAmounts() {
        MonthlySummaryView summary =
                new MonthlySummaryView(
                        1L, "2026-10", List.of(new AccountSummaryView(1L, "現金", 0)), 0);

        assertThat(MonthlySummaryFormatter.format(summary)).isEqualTo("2026-10 家庭支出匯總\n現金：0\n總計：0");
    }

    @Test
    void showsEmptyMessageWhenNoAccounts() {
        MonthlySummaryView summary = new MonthlySummaryView(1L, "2026-10", List.of(), 0);

        assertThat(MonthlySummaryFormatter.format(summary))
                .isEqualTo("2026-10 家庭支出匯總\n本月尚無任何支出紀錄，淨額為 0");
    }
}
