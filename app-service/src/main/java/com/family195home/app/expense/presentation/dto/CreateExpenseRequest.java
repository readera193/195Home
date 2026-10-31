package com.family195home.app.expense.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateExpenseRequest(
        @NotNull Long familyGroupId,
        @NotNull Long paymentAccountId,
        @NotNull BigDecimal amount,
        @NotBlank @Size(max = 500, message = "說明不可超過 500 個字") String note,
        LocalDateTime occurredAt) {}
