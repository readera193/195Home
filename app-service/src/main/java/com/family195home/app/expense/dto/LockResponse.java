package com.family195home.app.expense.dto;

import java.time.LocalDateTime;

public record LockResponse(Long expenseId, Long lockedByMemberId, LocalDateTime lockedAt) {
}
