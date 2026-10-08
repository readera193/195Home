package com.family195home.app.statistics.domain;

import java.util.List;

public record MonthlySummary(Long familyGroupId, String month, List<AccountSummary> accounts, long totalNetAmount) {
}
