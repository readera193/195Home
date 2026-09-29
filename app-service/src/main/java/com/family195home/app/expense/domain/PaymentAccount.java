package com.family195home.app.expense.domain;

import java.time.LocalDateTime;

public class PaymentAccount {

    private Long id;
    private Long familyMemberId;
    private Long familyGroupId;
    private String name;
    private PaymentAccountStatus status;
    private LocalDateTime createdAt;

    public PaymentAccount() {
    }

    public PaymentAccount(Long familyMemberId, Long familyGroupId, String name, PaymentAccountStatus status, LocalDateTime createdAt) {
        this.familyMemberId = familyMemberId;
        this.familyGroupId = familyGroupId;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFamilyMemberId() {
        return familyMemberId;
    }

    public void setFamilyMemberId(Long familyMemberId) {
        this.familyMemberId = familyMemberId;
    }

    public Long getFamilyGroupId() {
        return familyGroupId;
    }

    public void setFamilyGroupId(Long familyGroupId) {
        this.familyGroupId = familyGroupId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PaymentAccountStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentAccountStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
