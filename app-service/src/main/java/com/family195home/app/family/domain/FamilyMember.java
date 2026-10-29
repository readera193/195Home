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

    /** 離開或被移出：不再持有角色，避免離開者仍顯示為管理者，或重新加入時帶回管理權限。 */
    public void leave(MemberStatus newStatus, LocalDateTime at) {
        this.status = newStatus;
        this.role = MemberRole.MEMBER;
        this.leftAt = at;
    }

    /** 已離開成員以原邀請碼重新加入：恢復為在職並沿用原成員紀錄。 */
    public void rejoin() {
        this.status = MemberStatus.ACTIVE;
        this.leftAt = null;
    }

    /** 管理者恢復被移出成員的加入資格（REMOVED → LEFT）。 */
    public void allowRejoin() {
        this.status = MemberStatus.LEFT;
    }

    public void promoteToAdmin() {
        this.role = MemberRole.ADMIN;
    }

    public void demoteToMember() {
        this.role = MemberRole.MEMBER;
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    public boolean isAdmin() {
        return role == MemberRole.ADMIN;
    }
}
