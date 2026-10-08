package com.family195home.app.family.presentation.dto;

public record LoginResponse(String token, Long userId, String email, String expiresAt) {
}
