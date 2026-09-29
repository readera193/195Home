package com.family195home.notification.dto;

import java.util.List;

public record MonthlySummaryView(Long familyGroupId, String month, List<AccountSummaryView> accounts, long totalNetAmount) {
}
