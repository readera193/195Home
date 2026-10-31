package com.family195home.app.statistics.presentation.dto;

public record AccountSummaryView(
        Long paymentAccountId,
        String paymentAccountName,
        long incomeAmount,
        long expenseAmount,
        long netAmount) {}
