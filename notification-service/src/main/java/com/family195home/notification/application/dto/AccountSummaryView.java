package com.family195home.notification.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountSummaryView(
        Long paymentAccountId, String paymentAccountName, long netAmount) {}
