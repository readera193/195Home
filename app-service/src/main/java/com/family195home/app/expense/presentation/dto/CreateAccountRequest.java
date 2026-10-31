package com.family195home.app.expense.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotNull Long familyGroupId,
        @NotBlank @Size(max = 100, message = "帳戶名稱不可超過 100 個字") String name) {}
