package com.family195home.app.expense.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAccountRequest(@NotBlank String name) {
}
