package com.family195home.notification.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MonthlySummaryView(
        Long familyGroupId, String month, List<AccountSummaryView> accounts, long totalNetAmount) {}
