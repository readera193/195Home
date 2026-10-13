package com.family195home.app.family.domain;

import java.time.LocalDateTime;

public class User {

    private Long id;
    private String lineUserId;
    private String displayName;
    private LocalDateTime createdAt;

    public User() {
    }

    public User(String lineUserId, String displayName, LocalDateTime createdAt) {
        this.lineUserId = lineUserId;
        this.displayName = displayName;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLineUserId() {
        return lineUserId;
    }

    public void setLineUserId(String lineUserId) {
        this.lineUserId = lineUserId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
