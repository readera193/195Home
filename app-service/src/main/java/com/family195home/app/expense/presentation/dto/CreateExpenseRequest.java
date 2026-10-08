package com.family195home.app.expense.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateExpenseRequest(
        @NotNull Long familyGroupId,
        @NotNull Long paymentAccountId,
        @NotNull BigDecimal amount,
        @NotBlank String note,
        String occurredAt) {
}
