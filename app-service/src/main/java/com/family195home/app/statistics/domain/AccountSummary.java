package com.family195home.app.statistics.domain;

/** incomeAmount／expenseAmount 皆為正數（支出以絕對值表示），netAmount = incomeAmount − expenseAmount。 */
public record AccountSummary(
        Long paymentAccountId,
        String paymentAccountName,
        long incomeAmount,
        long expenseAmount,
        long netAmount) {}
