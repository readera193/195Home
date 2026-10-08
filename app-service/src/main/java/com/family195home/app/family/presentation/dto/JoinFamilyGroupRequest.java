package com.family195home.app.family.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinFamilyGroupRequest(@NotBlank String inviteCode) {
}
