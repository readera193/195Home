package com.family195home.app.family.domain;

import java.time.LocalDateTime;

public class LineBindingCode {

    private Long id;
    private Long familyMemberId;
    private String code;
    private LocalDateTime expiresAt;
    private boolean used;
    private LocalDateTime createdAt;

    public LineBindingCode() {
    }

    public LineBindingCode(Long familyMemberId, String code, LocalDateTime expiresAt, boolean used, LocalDateTime createdAt) {
        this.familyMemberId = familyMemberId;
        this.code = code;
        this.expiresAt = expiresAt;
        this.used = used;
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isValidAt(LocalDateTime now) {
        return !used && expiresAt.isAfter(now);
    }
}
