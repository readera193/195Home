package com.family195home.app.expense.dto;

import java.time.LocalDateTime;

public record ExpenseResponse(
        Long expenseId,
        Integer amount,
        String note,
        LocalDateTime occurredAt,
        Long paymentAccountId,
        String paymentAccountName,
        Long authorMemberId,
        boolean locked) {
}
