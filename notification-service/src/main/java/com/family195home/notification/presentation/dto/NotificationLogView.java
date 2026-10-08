package com.family195home.notification.presentation.dto;

public record NotificationLogView(
        Long familyMemberId, String lineUserId, String status, int attempts, String lastAttemptAt, String errorMessage) {
}
