package com.family195home.app.family.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinFamilyGroupRequest(@NotBlank String inviteCode) {
}
