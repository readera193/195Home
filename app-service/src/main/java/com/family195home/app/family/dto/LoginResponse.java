package com.family195home.app.family.dto;

public record LoginResponse(String token, Long userId, String email, String expiresAt) {
}
