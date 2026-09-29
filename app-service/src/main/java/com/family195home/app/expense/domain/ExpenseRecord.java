package com.family195home.app.expense.domain;

import java.time.LocalDateTime;

public class ExpenseRecord {

    private Long id;
    private Long familyGroupId;
    private Long paymentAccountId;
    private Long authorMemberId;
    private Integer amount;
    private String note;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long lockedByMemberId;
    private LocalDateTime lockedAt;

    public ExpenseRecord() {
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

    public Long getPaymentAccountId() {
        return paymentAccountId;
    }

    public void setPaymentAccountId(Long paymentAccountId) {
        this.paymentAccountId = paymentAccountId;
    }

    public Long getAuthorMemberId() {
        return authorMemberId;
    }

    public void setAuthorMemberId(Long authorMemberId) {
        this.authorMemberId = authorMemberId;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getLockedByMemberId() {
        return lockedByMemberId;
    }

    public void setLockedByMemberId(Long lockedByMemberId) {
        this.lockedByMemberId = lockedByMemberId;
    }

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }

    public boolean isLocked(LocalDateTime now, long lockTtlMinutes) {
        return lockedByMemberId != null && lockedAt != null && lockedAt.plusMinutes(lockTtlMinutes).isAfter(now);
    }
}
