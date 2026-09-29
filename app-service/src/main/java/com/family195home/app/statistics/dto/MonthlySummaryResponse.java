package com.family195home.app.statistics.dto;

import java.util.List;

public record MonthlySummaryResponse(Long familyGroupId, String month, List<AccountSummaryView> accounts, long totalNetAmount) {
}
