package com.family195home.app.family.domain;

import java.time.LocalDateTime;

public class LineBinding {

    private Long id;
    private Long familyMemberId;
    private String lineUserId;
    private LocalDateTime boundAt;

    public LineBinding() {
    }

    public LineBinding(Long familyMemberId, String lineUserId, LocalDateTime boundAt) {
        this.familyMemberId = familyMemberId;
        this.lineUserId = lineUserId;
        this.boundAt = boundAt;
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

    public String getLineUserId() {
        return lineUserId;
    }

    public void setLineUserId(String lineUserId) {
        this.lineUserId = lineUserId;
    }

    public LocalDateTime getBoundAt() {
        return boundAt;
    }

    public void setBoundAt(LocalDateTime boundAt) {
        this.boundAt = boundAt;
    }
}
