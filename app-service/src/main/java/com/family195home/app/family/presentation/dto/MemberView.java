package com.family195home.app.family.presentation.dto;

import java.time.LocalDateTime;

public record MemberView(
        Long familyMemberId,
        Long userId,
        String email,
        String status,
        String role,
        LocalDateTime joinedAt,
        LocalDateTime leftAt) {
}
