package com.family195home.app.family.domain;

import java.time.LocalDateTime;

public class FamilyMember {

    private Long id;
    private Long familyGroupId;
    private Long userId;
    private MemberStatus status;
    private MemberRole role;
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;

    public FamilyMember() {
    }

    public FamilyMember(Long familyGroupId, Long userId, MemberStatus status, MemberRole role, LocalDateTime joinedAt) {
        this.familyGroupId = familyGroupId;
        this.userId = userId;
        this.status = status;
        this.role = role;
        this.joinedAt = joinedAt;
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public void setStatus(MemberStatus status) {
        this.status = status;
    }

    public MemberRole getRole() {
        return role;
    }

    public void setRole(MemberRole role) {
        this.role = role;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    public LocalDateTime getLeftAt() {
        return leftAt;
    }

    public void setLeftAt(LocalDateTime leftAt) {
        this.leftAt = leftAt;
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    public boolean isAdmin() {
        return role == MemberRole.ADMIN;
    }
}
