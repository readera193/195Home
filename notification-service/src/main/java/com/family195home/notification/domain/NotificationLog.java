package com.family195home.notification.domain;

import java.time.LocalDateTime;

public class NotificationLog {

    private Long id;
    private Long familyGroupId;
    private Long familyMemberId;
    private String lineUserId;
    private String yearMonth;
    private NotificationStatus status;
    private int attempts;
    private LocalDateTime lastAttemptAt;
    private String errorMessage;

    public NotificationLog() {
    }

    public NotificationLog(Long familyGroupId, Long familyMemberId, String lineUserId, String yearMonth) {
        this.familyGroupId = familyGroupId;
        this.familyMemberId = familyMemberId;
        this.lineUserId = lineUserId;
        this.yearMonth = yearMonth;
        this.attempts = 0;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFamilyGroupId() {
        return familyGroupId;
    }

    public void setFamilyGroupId(Long familyGroupId) {
        this.familyGroupId = familyGroupId;
    }

    public Long getFamilyMemberId() {
        return familyMemberId;
    }

    public void setFamilyMemberId(Long familyMemberId) {
        this.familyMemberId = familyMemberId;
    }

    public String getLineUserId() {
        return lineUserId;
    }

    public void setLineUserId(String lineUserId) {
        this.lineUserId = lineUserId;
    }

    public String getYearMonth() {
        return yearMonth;
    }

    public void setYearMonth(String yearMonth) {
        this.yearMonth = yearMonth;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public LocalDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(LocalDateTime lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
