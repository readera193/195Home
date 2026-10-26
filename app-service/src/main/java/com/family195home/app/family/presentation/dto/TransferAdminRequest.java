package com.family195home.app.family.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record TransferAdminRequest(@NotNull Long newAdminMemberId) {
}
