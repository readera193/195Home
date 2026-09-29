package com.family195home.app.family.domain;

import java.time.LocalDateTime;

public class FamilyGroup {

    private Long id;
    private String name;
    private FamilyGroupStatus status;
    private String inviteCode;
    private LocalDateTime createdAt;

    public FamilyGroup() {
    }

    public FamilyGroup(String name, FamilyGroupStatus status, String inviteCode, LocalDateTime createdAt) {
        this.name = name;
        this.status = status;
        this.inviteCode = inviteCode;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public FamilyGroupStatus getStatus() {
        return status;
    }

    public void setStatus(FamilyGroupStatus status) {
        this.status = status;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
