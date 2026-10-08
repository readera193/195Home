package com.family195home.app.statistics.presentation.dto;

import com.family195home.app.statistics.domain.MonthlySummary;

import java.util.List;

public record MonthlySummaryResponse(Long familyGroupId, String month, List<AccountSummaryView> accounts, long totalNetAmount) {

    public static MonthlySummaryResponse from(MonthlySummary summary) {
        List<AccountSummaryView> accounts = summary.accounts().stream()
                .map(a -> new AccountSummaryView(a.paymentAccountId(), a.paymentAccountName(), a.netAmount()))
                .toList();
        return new MonthlySummaryResponse(summary.familyGroupId(), summary.month(), accounts, summary.totalNetAmount());
    }
}
