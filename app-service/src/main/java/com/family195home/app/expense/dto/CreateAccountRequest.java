package com.family195home.app.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(@NotNull Long familyGroupId, @NotBlank String name) {
}
