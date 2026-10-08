package com.family195home.app.family.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record ConsumeCodeRequest(@NotBlank String code, @NotBlank String lineUserId) {
}
